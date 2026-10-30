// Ndopify — listing page: the "Demander une visite" popup, the "Poser une question" drawer, the photo viewer and the
// share button. Everything only enhances: without JavaScript the popup and the drawer open with :target and their
// forms post, the photos are links.
(function () {
  'use strict';

  document.documentElement.classList.add('js');

  var ITI_BASE = 'https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/js/';

  function el(tag, className, html) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (html !== undefined) node.innerHTML = html;
    return node;
  }

  // ---------------------------------------------------------------- Popup and drawer

  // Any [data-modal-open] link opens the dialog its href names (#visit, #ask), [data-modal-close] closes it. One
  // dialog at a time: opening one from the other ("Demander une visite" in the drawer) replaces it, and focus goes
  // back to what opened the first one.
  function initDialogs() {
    var current = null;

    function focusables(dialog) {
      return Array.prototype.filter.call(
        dialog.querySelector('.modal__panel')
          .querySelectorAll('a[href], button, textarea, input, select, [tabindex]:not([tabindex="-1"])'),
        function (n) { return !n.disabled && n.offsetParent !== null; }
      );
    }

    function hide(dialog) {
      dialog.classList.remove('is-open');
      if (location.hash === '#' + dialog.id) history.replaceState(null, '', location.pathname + location.search);
    }

    function open(dialog, trigger) {
      var opener = trigger || document.activeElement;
      if (current) {
        opener = current.opener;
        hide(current.dialog);
      }
      current = { dialog: dialog, opener: opener };
      dialog.classList.add('is-open');
      dialog.setAttribute('role', 'dialog');
      dialog.setAttribute('aria-modal', 'true');
      document.body.classList.add('modal-open');
      var first = dialog.querySelector(
        '[data-interest-form]:not([hidden]) input, [data-interest-done]:not([hidden]), [data-chat-form] textarea');
      if (first) first.focus();
    }

    function close() {
      if (!current) return;
      hide(current.dialog);
      document.body.classList.remove('modal-open');
      if (current.opener && typeof current.opener.focus === 'function') current.opener.focus();
      current = null;
    }

    document.addEventListener('click', function (e) {
      var trigger = e.target.closest('[data-modal-open]');
      var dialog = trigger && document.getElementById(trigger.getAttribute('href').slice(1));
      if (dialog) {
        e.preventDefault();
        open(dialog, trigger);
        return;
      }
      if (e.target.closest('[data-modal-close]')) {
        e.preventDefault();
        close();
      }
    });

    document.addEventListener('keydown', function (e) {
      if (!current) return;
      if (e.key === 'Escape') {
        close();
      } else if (e.key === 'Tab') {
        var items = focusables(current.dialog);
        if (!items.length) return;
        var first = items[0];
        var last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });

    // Opened on load when the page comes back from the server with #visit or #ask, or from a shared link
    var target = location.hash && document.getElementById(location.hash.slice(1));
    if (target && target.classList.contains('modal')) open(target, null);
  }

  // ---------------------------------------------------------------- Visit form

  // "Envoyer la demande" stays inactive until every [required] field is filled (presence, not format); the format is
  // checked on submit, in the fields' order. The prototype then shows the confirmation instead of posting.
  function initVisitForm(modal) {
    var form = modal.querySelector('[data-interest-form]');
    var done = modal.querySelector('[data-interest-done]');
    var phoneInput = form.querySelector('[data-phone-name]');
    var submit = form.querySelector('[type="submit"]');
    var iti = null;
    var phoneBroken = false;

    if (window.intlTelInput) {
      // The library's hidden input carries the name and the E.164 value; the visible one is only for typing
      phoneInput.removeAttribute('name');
      iti = window.intlTelInput(phoneInput, {
        initialCountry: 'cm',
        countryOrder: ['cm', 'ga', 'cg', 'td', 'cf', 'gq'],
        separateDialCode: true,
        strictMode: true,
        validationNumberTypes: ['MOBILE'],
        hiddenInput: function () { return { phone: phoneInput.dataset.phoneName }; },
        loadUtils: function () { return import(ITI_BASE + 'utils.js'); }
      });
      // Typing before utils.js is there would break strictMode's formatting
      phoneInput.readOnly = true;
      iti.promise.then(function () {
        phoneInput.readOnly = false;
      }).catch(function () {
        phoneBroken = true;
        showError(phoneInput, 'Impossible de vérifier les numéros pour le moment. Réessayez dans un instant.');
        refresh();
      });
    }

    // Keeps the hidden whatsappNumber in E.164 as the number is typed: empty while it isn't a valid number
    function syncPhone() {
      var hidden = form.querySelector('input[type="hidden"][name="' + phoneInput.dataset.phoneName + '"]');
      if (!hidden || !iti) return;
      var valid = window.intlTelInput.utils && iti.isValidNumberPrecise();
      hidden.value = valid ? iti.getNumber(window.intlTelInput.utils.numberFormat.E164) : '';
    }

    function refresh() {
      syncPhone();
      var filled = Array.prototype.every.call(form.querySelectorAll('[required]'), function (c) {
        // A radio's value is always there: its group is filled once one of them is checked
        if (c.type === 'radio') return !!form.querySelector('[name="' + c.name + '"]:checked');
        return c.value.trim() !== '';
      });
      submit.setAttribute('aria-disabled', String(!filled || phoneBroken));
    }

    form.addEventListener('input', refresh);
    form.addEventListener('change', refresh);
    phoneInput.addEventListener('countrychange', refresh);
    refresh();

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (submit.getAttribute('aria-disabled') === 'true') return;
      var firstName = form.querySelector('#first-name');
      var lastName = form.querySelector('#last-name');
      var email = form.querySelector('#email');
      var visitType = form.querySelector('[name="visitType"]');
      var checkedType = form.querySelector('[name="visitType"]:checked');
      var visitDate = form.querySelector('[name="visitDate"]');
      var checkedDate = form.querySelector('[name="visitDate"]:checked');
      var visitTime = form.querySelector('#visit-time');
      var ok = [
        showError(firstName, firstName.value.trim() ? '' : 'Indiquez votre prénom.'),
        showError(lastName, lastName.value.trim() ? '' : 'Indiquez votre nom.'),
        showError(email, /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())
          ? '' : 'Cette adresse e-mail n\'est pas valide, vérifiez-la.'),
        showError(phoneInput, phoneError()),
        showError(visitType, checkedType ? '' : 'Choisissez une visite sur place ou en vidéo.'),
        showError(visitDate, checkedDate ? '' : 'Choisissez le jour de la visite.'),
        showError(visitTime, visitTime.value ? '' : 'Choisissez l\'heure de la visite.')
      ].every(Boolean);
      if (!ok) {
        form.querySelector('[aria-invalid="true"]').focus();
        return;
      }
      var phone = iti.getNumber(window.intlTelInput.utils.numberFormat.E164);
      // The day card's parts (lun. / 2 / nov.) have no space between them
      var day = Array.prototype.map.call(checkedDate.closest('label').children, function (n) {
        return n.textContent.trim();
      }).filter(Boolean).join(' ');
      var time = visitTime.options[visitTime.selectedIndex].text;
      form.hidden = true;
      done.querySelector('[data-interest-done-text]').textContent = 'Merci ' + firstName.value.trim() + ' ! ' +
        'Vous avez demandé une visite ' + (checkedType.value === 'VIDEO' ? 'en vidéo' : 'sur place') + ' le ' + day +
        ' à ' + time + '. L\'agent vous confirme le créneau sur WhatsApp au ' + formatPhone(phone) +
        (checkedType.value === 'VIDEO' ? ', et vous appelle en vidéo sur WhatsApp à l\'heure prévue.' : '.');
      done.hidden = false;
      done.focus();
    });

    function phoneError() {
      if (!iti || !window.intlTelInput.utils) return 'Impossible de vérifier ce numéro pour le moment.';
      if (iti.isValidNumberPrecise()) return '';
      var codes = window.intlTelInput.utils.validationError;
      switch (iti.getValidationError()) {
        case codes.TOO_SHORT: return 'Ce numéro est trop court, il manque des chiffres.';
        case codes.TOO_LONG: return 'Ce numéro est trop long, vérifiez-le.';
        case codes.INVALID_COUNTRY_CODE: return 'L\'indicatif du pays n\'est pas valide.';
        default: return 'Ce numéro n\'est pas un numéro mobile valide, vérifiez-le.';
      }
    }
  }

  function formatPhone(e164) {
    var utils = window.intlTelInput && window.intlTelInput.utils;
    return utils ? utils.formatNumber(e164, null, utils.numberFormat.INTERNATIONAL) : e164;
  }

  function showError(control, message) {
    var error = control.closest('.field').querySelector('.field-error');
    control.setAttribute('aria-invalid', String(!!message));
    if (error) error.textContent = message;
    return !message;
  }

  // ---------------------------------------------------------------- Question drawer

  // The prototype answers from canned replies, picked by keyword; the real assistant is the server's (POST of the
  // question, answer from the listing's public information). Enter sends, Shift+Enter starts a new line.
  var ANSWERS = [
    {
      keys: ['charge', 'compteur', 'facture', 'électricité', 'electricite', 'courant'],
      text: 'Le logement a ses propres compteurs d\'eau et d\'électricité : vous ne payez que votre consommation, ' +
        'en plus du loyer de 175K FCFA par mois. Il y a aussi un groupe électrogène en cas de coupure.'
    },
    {
      keys: ['eau', 'forage', 'réservoir'],
      text: 'Oui : en plus du compteur d\'eau dédié, l\'immeuble a un forage et un réservoir d\'eau, ce qui assure ' +
        'l\'eau même pendant les coupures du réseau.'
    },
    {
      keys: ['payer', 'entrée', 'caution', 'avance', 'combien', 'prix', 'loyer'],
      text: 'À l\'entrée, il faut prévoir :\n• Caution : 2 mois, soit 350K FCFA\n• Avance : 3 mois, soit 525K FCFA\n' +
        'Soit 875K FCFA au total, pour un bail de 12 mois.'
    },
    {
      keys: ['visite', 'visiter', 'voir', 'rendez'],
      text: 'Dans « Demander une visite », choisissez une visite sur place ou en vidéo (un appel vidéo WhatsApp avec ' +
        'l\'agent), puis un jour et une heure : l\'agent vous confirme le créneau sur WhatsApp. La visite se règle avec le Pass Visite par Mobile Money, jamais en espèces.',
      visit: true
    },
    {
      keys: ['quartier', 'sécurité', 'securite', 'sûr', 'gardien', 'calme'],
      text: 'Bonapriso est un quartier résidentiel. L\'immeuble est clôturé, avec un gardien jour et nuit et de la ' +
        'vidéosurveillance. Les commerces de la rue Njo-Njo sont à cinq minutes à pied.'
    },
    {
      keys: ['disponible', 'quand', 'emménager', 'date'],
      text: 'Le logement est disponible à partir du 1er décembre 2026, pour un bail de 12 mois (préavis d\'un mois).'
    }
  ];
  var FALLBACK = {
    text: 'Je n\'ai pas cette information dans l\'annonce. L\'agent pourra vous répondre lors de la visite.',
    visit: true
  };

  function answer(question) {
    var q = question.toLowerCase();
    for (var i = 0; i < ANSWERS.length; i++) {
      if (ANSWERS[i].keys.some(function (k) { return q.indexOf(k) !== -1; })) return ANSWERS[i];
    }
    return FALLBACK;
  }

  function initChat(drawer) {
    var form = drawer.querySelector('[data-chat-form]');
    var input = form.querySelector('textarea');
    var send = form.querySelector('[type="submit"]');
    var body = drawer.querySelector('[data-chat-body]');
    var messages = drawer.querySelector('[data-chat-messages]');
    var suggestions = drawer.querySelector('[data-chat-suggestions]');
    var waiting = false;
    // The required question only stops an empty post without JavaScript; here aria-disabled does
    form.noValidate = true;

    function add(kind, text) {
      var msg = el('li', 'chat__msg chat__msg--' + kind);
      var who = el('span', 'visually-hidden');
      who.textContent = kind === 'bot' ? 'Assistant : ' : 'Vous : ';
      var content = el('span', 'chat__text');
      content.textContent = text;
      msg.appendChild(who);
      msg.appendChild(content);
      messages.appendChild(msg);
      body.scrollTop = body.scrollHeight;
      return msg;
    }

    function refresh() {
      send.setAttribute('aria-disabled', String(waiting || input.value.trim() === ''));
      // Grows with the question, up to the CSS max-height
      input.style.height = 'auto';
      input.style.height = input.scrollHeight + 'px';
    }

    function ask(question) {
      if (waiting || !question) return;
      waiting = true;
      suggestions.hidden = true;
      add('user', question);
      input.value = '';
      refresh();
      var typing = el('li', 'chat__msg chat__msg--bot',
        '<span class="visually-hidden">L\'assistant écrit…</span>' +
        '<span aria-hidden="true" class="chat__typing"><span></span><span></span><span></span></span>');
      messages.appendChild(typing);
      body.scrollTop = body.scrollHeight;
      setTimeout(function () {
        typing.remove();
        var reply = answer(question);
        var msg = add('bot', reply.text);
        if (reply.visit) {
          var link = el('a', 'btn btn-secondary chat__visit',
            '<svg aria-hidden="true" class="icon"><use href="#i-calendar"/></svg>Demander une visite');
          link.href = '#visit';
          link.setAttribute('data-modal-open', '');
          msg.appendChild(link);
        }
        body.scrollTop = body.scrollHeight;
        waiting = false;
        refresh();
      }, 900);
    }

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      // A suggestion is a submit button of the form (it posts without JavaScript)
      var suggestion = e.submitter && e.submitter.name === 'suggestion' ? e.submitter.value : '';
      if (!suggestion && send.getAttribute('aria-disabled') === 'true') return;
      ask(suggestion || input.value.trim());
      if (!suggestion) input.focus();
    });

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        form.requestSubmit(send);
      }
    });
    input.addEventListener('input', refresh);
    refresh();
  }

  // ---------------------------------------------------------------- Photo viewer

  // The photos are the #listing-photos list ({src, alt}, alt as caption): only the gallery's are in the page, the
  // others load when shown. Any [data-photo] opens the viewer at that index. The track is a scroll-snap strip:
  // swiping scrolls it, the buttons and arrows too.
  function initLightbox() {
    var box = document.querySelector('[data-lightbox]');
    var data = document.getElementById('listing-photos');
    if (!box || !data) return;
    var photos = JSON.parse(data.textContent);
    var track = box.querySelector('[data-lightbox-track]');
    var counter = box.querySelector('[data-lightbox-counter]');
    var prev = box.querySelector('[data-lightbox-prev]');
    var next = box.querySelector('[data-lightbox-next]');
    var opener = null;
    var current = 0;

    photos.forEach(function (photo, i) {
      var slide = el('li', 'lightbox__slide');
      slide.setAttribute('aria-roledescription', 'photo');
      slide.setAttribute('aria-label', (i + 1) + ' sur ' + photos.length);
      var figure = el('figure');
      var img = el('img');
      img.alt = photo.alt;
      img.dataset.src = photo.src;
      img.decoding = 'async';
      var caption = el('figcaption');
      caption.textContent = photo.alt;
      figure.appendChild(img);
      figure.appendChild(caption);
      slide.appendChild(figure);
      track.appendChild(slide);
    });
    var slides = track.children;

    // Loads the shown photo and its neighbours only
    function load(index) {
      [index - 1, index, index + 1].forEach(function (i) {
        var img = slides[i] && slides[i].querySelector('img');
        if (img && !img.src) img.src = img.dataset.src;
      });
    }

    function update(index) {
      current = index;
      counter.textContent = (index + 1) + ' / ' + photos.length;
      prev.hidden = index === 0;
      next.hidden = index === photos.length - 1;
      load(index);
    }

    function go(index, smooth) {
      index = Math.max(0, Math.min(photos.length - 1, index));
      track.scrollTo({ left: index * track.clientWidth, behavior: smooth ? 'smooth' : 'auto' });
      update(index);
    }

    function open(index, trigger) {
      opener = trigger;
      box.hidden = false;
      document.body.classList.add('modal-open');
      go(index, false);
      box.querySelector('[data-lightbox-close]').focus();
    }

    function close() {
      box.hidden = true;
      document.body.classList.remove('modal-open');
      if (opener) opener.focus();
    }

    document.addEventListener('click', function (e) {
      var trigger = e.target.closest('[data-photo]');
      if (!trigger) return;
      e.preventDefault();
      open(parseInt(trigger.dataset.photo, 10), trigger);
    });

    prev.addEventListener('click', function () { go(current - 1, true); });
    next.addEventListener('click', function () { go(current + 1, true); });
    box.querySelector('[data-lightbox-close]').addEventListener('click', close);

    // A click on the dark area around the photo closes the viewer
    track.addEventListener('click', function (e) {
      if (!e.target.closest('img, figcaption')) close();
    });

    var scrollTimer;
    track.addEventListener('scroll', function () {
      clearTimeout(scrollTimer);
      scrollTimer = setTimeout(function () {
        var index = Math.round(track.scrollLeft / track.clientWidth);
        if (index !== current) update(index);
      }, 80);
    });

    window.addEventListener('resize', function () {
      if (!box.hidden) go(current, false);
    });

    box.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        close();
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        go(current - 1, true);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        go(current + 1, true);
      } else if (e.key === 'Tab') {
        var items = Array.prototype.filter.call(box.querySelectorAll('button'), function (b) { return !b.hidden; });
        var first = items[0];
        var last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });
  }

  // ---------------------------------------------------------------- Share

  function initShare() {
    var button = document.querySelector('[data-share]');
    if (!button || !navigator.share) return;
    button.hidden = false;
    button.addEventListener('click', function () {
      navigator.share({ title: document.title, url: location.href }).catch(function () { /* dismissed */ });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initDialogs();
    var visit = document.getElementById('visit');
    if (visit) initVisitForm(visit);
    var ask = document.getElementById('ask');
    if (ask) initChat(ask);
    initLightbox();
    initShare();
  });
})();
