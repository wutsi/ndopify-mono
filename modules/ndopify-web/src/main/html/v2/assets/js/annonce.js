// Ndopify — listing page: the "Être contacté par un agent" popup, the photo viewer and the share button; its
// popups code is shared with proposition.html and visite.html. Everything only enhances: without JavaScript the popups
// open with :target and their forms post, the photos are links. #380's ?state=email-invalid / -empty samples are the
// prototype's switch, like ?sale=1 and ?state=sold; production renders the same state server-side.
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

  // ---------------------------------------------------------------- Sale variant

  // ?sale=1: the page is the SALE listing (a villa, in annonce.html; the buyer's search in proposition.html). Its sale
  // words are in the markup ([data-sale-only] blocks, [data-rental-only] for the rental ones): this only toggles them,
  // so every word is right without JavaScript. &state=sold is the SOLD state: badge "Vendu", no interest form, no call
  // to action and no conditions (data-sold-hide), as a sold listing is served today.
  var params = new URLSearchParams(location.search);
  // v2 (#274): ?building=1 is a building sold as a whole (a sale); ?sale=1 alone is the villa.
  var isBuilding = params.get('building') === '1';
  var isSale = params.get('sale') === '1' || isBuilding;
  var listingState = params.get('state');
  var isSold = isSale && listingState === 'sold';
  // #380: the interest popup's POST was refused on the e-mail (400 EMAIL_ADDRESS_INVALID). The page comes back with
  // #visit open, the typed values and the shared sentence under the flagged e-mail; -empty leaves the other fields
  // blank (the visitor only typed the e-mail). The global popup message is not shown for either. Only this listing
  // page has the state: proposition.html and visite.html share this script but their #visit is another popup.
  var isListingPage = !!document.querySelector('[data-listing-page]');
  var isEmailInvalid = isListingPage &&
    (listingState === 'email-invalid' || listingState === 'email-invalid-empty');
  var isEmailInvalidEmpty = isEmailInvalid && listingState === 'email-invalid-empty';

  function showAll(selector, shown) {
    document.querySelectorAll(selector).forEach(function (node) {
      node.hidden = !shown;
    });
  }

  function initListingVariant() {
    showAll('[data-rental-only]', !isSale);
    showAll('[data-sale-only]', isSale);
    // A sale has one property at a time: the villa's blocks, or the building's.
    showAll('[data-property-only]', isSale && !isBuilding);
    showAll('[data-building-only]', isBuilding);
    showAll('[data-no-building]', !isBuilding);
    showAll('[data-sold-only]', isSold);
    if (isSold) {
      showAll('[data-interest-cta]', false);
      showAll('[data-price-terms]', false);
      showAll('[data-sale-badge]', false);
      showAll('[data-sold-hide]', false);
      // No form at all once sold: the popup is not only unreachable, it is out of the page
      var popup = document.getElementById('visit');
      if (popup) popup.hidden = true;
    }
    if (!isSale) return;
    // annonce.html only: the property's tab title, its description and the photo viewer's label
    if (document.querySelector('[data-listing-page]')) {
      var description = document.querySelector('meta[name="description"]');
      if (isBuilding) {
        document.title = 'Immeuble 12 unités à vendre, Bonapriso — Douala | Ndopify';
        if (description) {
          description.setAttribute('content', 'Immeuble de 12 unités sur 3 étages à vendre à Bonapriso, Douala. ' +
            '180M FCFA. 850 m², photos et surface vérifiées sur place ; confiez votre recherche à un agent vérifié ' +
            'du secteur.');
        }
      } else {
        document.title = 'Villa 5 chambres à vendre, Bonapriso — Douala | Ndopify';
        if (description) {
          description.setAttribute('content', 'Villa 5 chambres à vendre à Bonapriso, Douala. 250M FCFA. ' +
            'Photos, surface et position vérifiées sur place ; confiez votre recherche à un agent vérifié du secteur.');
        }
      }
    }
    var lightbox = document.querySelector('[data-lightbox]');
    if (lightbox) lightbox.setAttribute('aria-label', isBuilding ? "Photos de l'immeuble" : 'Photos du bien');
  }

  // ---------------------------------------------------------------- Interest error (#380)

  // The values the server renders again on 400 EMAIL_ADDRESS_INVALID: everything the visitor typed, the e-mail
  // flagged. The sentence itself is the field's static text; this only reveals it and fills the fields.
  var EMAIL_INVALID_EMAIL = 'audrey.kemayou@gmial.com';

  function initInterestError() {
    if (!isEmailInvalid || isSold) return;
    var visit = document.getElementById('visit');
    var form = visit && visit.querySelector('[data-interest-form]');
    if (!form) return;
    var email = form.querySelector('#email');
    if (!isEmailInvalidEmpty) {
      form.querySelector('#first-name').value = 'Audrey';
      form.querySelector('#last-name').value = 'Kemayou';
      form.querySelector('#whatsapp').value = '6 90 33 44 55';
      form.querySelector('[name="movingPeriod"][value="WITHIN_3_MONTHS"]').checked = true;
    }
    email.value = EMAIL_INVALID_EMAIL;
    // The first edit replaces the server's verdict with the field's own check (initVisitForm).
    email.dataset.serverValue = EMAIL_INVALID_EMAIL;
    email.setAttribute('aria-invalid', 'true');
    var error = form.querySelector('#email-error');
    if (error) error.hidden = false;
  }

  // ---------------------------------------------------------------- Popups

  // Any [data-modal-open] link opens the dialog its href names (#visit, #reject, #cancel…), [data-modal-close] closes
  // it. One dialog at a time: opening one from another replaces it, and focus goes back to what opened the first one.
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
        '[data-interest-form]:not([hidden]) input, [data-interest-done]:not([hidden])');
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

    // Opened on load when the page comes back from the server with #visit, or from a shared link. A dialog the page
    // took out (a SOLD sale's #visit) never opens.
    var target = location.hash && document.getElementById(location.hash.slice(1));
    if (target && !target.hidden && target.classList.contains('modal')) {
      open(target, null);
    } else if (isEmailInvalid && !isSold) {
      // #380: the server's re-render answers the interest POST, so the popup is already open (production's is-open).
      var visit = document.getElementById('visit');
      if (visit && !visit.hidden) open(visit, null);
    }
  }

  // ---------------------------------------------------------------- Visit form

  // "Envoyer ma demande" stays inactive until every [required] field is filled (presence, not format); the format is
  // checked on submit, in the fields' order. The mandate's criteria — budget included — come from the listing. The
  // prototype then shows the confirmation instead of posting.
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

    // #380: the server-rendered e-mail refusal goes away as soon as the visitor edits the address.
    var emailInput = form.querySelector('#email');
    var serverEmail = emailInput.dataset.serverValue;
    if (serverEmail !== undefined) {
      emailInput.addEventListener('input', function () {
        if (emailInput.value.trim() !== serverEmail) showError(emailInput, '');
      });
    }

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (submit.getAttribute('aria-disabled') === 'true') return;
      var firstName = form.querySelector('#first-name');
      var lastName = form.querySelector('#last-name');
      var email = form.querySelector('#email');
      var movingPeriod = form.querySelector('[name="movingPeriod"]');
      var checkedPeriod = form.querySelector('[name="movingPeriod"]:checked');
      var ok = [
        showError(firstName, firstName.value.trim() ? '' : 'Indiquez votre prénom.'),
        showError(lastName, lastName.value.trim() ? '' : 'Indiquez votre nom.'),
        showError(email, /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())
          ? '' : 'Cette adresse e-mail n\'est pas valide, vérifiez-la.'),
        showError(phoneInput, phoneError()),
        showError(movingPeriod, checkedPeriod ? '' : (isSale ? 'Choisissez quand vous souhaitez acheter.'
          : 'Choisissez votre date d\'emménagement.'))
      ].every(Boolean);
      if (!ok) {
        form.querySelector('[aria-invalid="true"]').focus();
        return;
      }
      form.hidden = true;
      done.querySelector('[data-interest-done-text]').textContent = 'Merci ' + firstName.value.trim() + ' ! ' +
        'Un agent vous contacte sous 24 h par WhatsApp ou par e-mail pour organiser la visite.';
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

  function showError(control, message) {
    var error = control.closest('.field').querySelector('.field-error');
    control.setAttribute('aria-invalid', String(!!message));
    if (error) {
      error.textContent = message;
      error.hidden = !message;
    }
    return !message;
  }

  // ---------------------------------------------------------------- Photo viewer

  // The photos are the #listing-photos list ({src, alt}, alt as caption): only the gallery's are in the page, the
  // others load when shown. Any [data-photo] opens the viewer at that index. The track is a scroll-snap strip:
  // swiping scrolls it, the buttons and arrows too.
  function initLightbox() {
    var box = document.querySelector('[data-lightbox]');
    var data = document.getElementById(isBuilding ? 'listing-photos-building'
      : isSale ? 'listing-photos-sale' : 'listing-photos');
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
    initListingVariant();
    initInterestError();
    initDialogs();
    var visit = document.getElementById('visit');
    // proposition.html's #visit popup has no contact fields (the mandate's): proposition.js does its form
    if (visit && visit.querySelector('[data-phone-name]')) initVisitForm(visit);
    initLightbox();
    initShare();
  });
})();
