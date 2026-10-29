// Ndopify — listing page: the "Je suis intéressé(e)" popup, the photo viewer and the share button. Everything only
// enhances: without JavaScript the popup opens with :target and its form posts, the photos are links.
(function () {
  'use strict';

  document.documentElement.classList.add('js');

  var ITI_BASE = 'https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/js/';
  var AGENT = 'Armelle';

  function el(tag, className, html) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (html !== undefined) node.innerHTML = html;
    return node;
  }

  // ---------------------------------------------------------------- Popup

  function initModal() {
    var modal = document.getElementById('interest');
    if (!modal) return null;
    var panel = modal.querySelector('.modal__panel');
    var opener = null;

    function focusables() {
      return Array.prototype.filter.call(
        panel.querySelectorAll('a[href], button, textarea, input, select, [tabindex]:not([tabindex="-1"])'),
        function (n) { return !n.disabled && n.offsetParent !== null; }
      );
    }

    function open(trigger) {
      opener = trigger || document.activeElement;
      modal.classList.add('is-open');
      modal.setAttribute('role', 'dialog');
      modal.setAttribute('aria-modal', 'true');
      document.body.classList.add('modal-open');
      var first = modal.querySelector('[data-interest-form]:not([hidden]) input, [data-interest-done]:not([hidden])');
      if (first) first.focus();
    }

    function close() {
      modal.classList.remove('is-open');
      document.body.classList.remove('modal-open');
      if (location.hash === '#interest') history.replaceState(null, '', location.pathname + location.search);
      if (opener && typeof opener.focus === 'function') opener.focus();
    }

    document.addEventListener('click', function (e) {
      var trigger = e.target.closest('[data-modal-open]');
      if (trigger) {
        e.preventDefault();
        open(trigger);
        return;
      }
      if (e.target.closest('[data-modal-close]')) {
        e.preventDefault();
        close();
      }
    });

    modal.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        close();
      } else if (e.key === 'Tab') {
        var items = focusables();
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

    // Opened on load when the page comes back from the server with #interest (errors), or from a shared link
    if (location.hash === '#interest') open(null);
    return modal;
  }

  // ---------------------------------------------------------------- Interest form

  // "Envoyer" stays inactive until every [required] field is filled (presence, not format); the format is checked on
  // submit, in the fields' order. The prototype then shows the confirmation instead of posting.
  function initInterestForm(modal) {
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
      var message = form.querySelector('#interest-message');
      var ok = [
        showError(firstName, firstName.value.trim() ? '' : 'Indiquez votre prénom.'),
        showError(lastName, lastName.value.trim() ? '' : 'Indiquez votre nom.'),
        showError(email, /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())
          ? '' : 'Cette adresse e-mail n\'est pas valide, vérifiez-la.'),
        showError(phoneInput, phoneError()),
        showError(message, message.value.trim() ? '' : 'Écrivez un message pour l\'agent.')
      ].every(Boolean);
      if (!ok) {
        form.querySelector('[aria-invalid="true"]').focus();
        return;
      }
      var phone = iti.getNumber(window.intlTelInput.utils.numberFormat.E164);
      form.hidden = true;
      done.querySelector('[data-interest-done-text]').textContent = 'Merci ' + firstName.value.trim() + ' ! ' +
        AGENT + ' vous recontacte très vite sur WhatsApp au ' + formatPhone(phone) + ' pour organiser la visite.';
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
    var modal = initModal();
    if (modal) initInterestForm(modal);
    initLightbox();
    initShare();
  });
})();
