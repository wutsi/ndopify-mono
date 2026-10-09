// Ndopify Partner Central — front-end behavior.
// Static prototype: no API is wired yet. Every "TODO(api)" marks where the server call replaces the redirect.
(function () {
  'use strict';

  var OTP_LENGTH = 6;
  var OTP_TTL_SECONDS = 5 * 60;
  var MAX_FILE_BYTES = 5 * 1024 * 1024;
  var ALLOWED_TYPES = ['image/jpeg', 'image/png'];

  function qs(sel, root) { return (root || document).querySelector(sel); }
  function qsa(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function param(name) { return new URLSearchParams(window.location.search).get(name); }

  var STATUSES = ['non-verifie', 'en-attente', 'verifie'];

  // TODO(api): read the status from the agent profile. The server must also refuse the agent-area data
  // to unverified agents: this client-side gate is only a convenience, not a security control.
  function accountStatus() {
    var fromUrl = param('statut');
    try {
      if (STATUSES.indexOf(fromUrl) !== -1) { sessionStorage.setItem('account-status', fromUrl); return fromUrl; }
      var stored = sessionStorage.getItem('account-status');
      if (STATUSES.indexOf(stored) !== -1) return stored;
    } catch (e) { /* storage unavailable: fall through to the safe default */ }
    return 'non-verifie';
  }

  function initAccessGuard() {
    var verified = accountStatus() === 'verifie';
    if (!verified && qs('[data-requires-verified-page]')) {
      window.location.replace('tableau-de-bord.html');
      return;
    }
    qsa('[data-requires-verified]').forEach(function (el) { el.hidden = !verified; });
  }

  function initNavToggle() {
    var toggle = qs('.nav-toggle');
    var list = qs('.site-nav__list');
    if (!toggle || !list) return;
    toggle.addEventListener('click', function () {
      var open = list.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    list.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        list.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.focus();
      }
    });
  }

  /* Inline validation (on blur, message beside the field) */
  function validate(control) {
    var field = control.closest('.field');
    var out = field && qs('.field-error', field);
    var ok = control.checkValidity();
    control.setAttribute('aria-invalid', String(!ok));
    if (out) {
      out.textContent = ok ? '' : (control.validity.valueMissing ? 'Ce champ est requis.' :
        control.type === 'email' ? 'Saisissez une adresse e-mail valide, par exemple nom@exemple.com.' :
          'Vérifiez la valeur saisie.');
    }
    return ok;
  }

  function initValidatedForm(form, onValid) {
    form.setAttribute('novalidate', '');
    form.addEventListener('focusout', function (e) {
      if (e.target.matches('input, select') && e.target.willValidate && e.target.value !== '') validate(e.target);
    });
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var firstInvalid = null;
      qsa('input[required], select[required]', form).forEach(function (c) {
        if (!validate(c) && !firstInvalid) firstInvalid = c;
      });
      if (firstInvalid) { firstInvalid.focus(); return; }
      onValid(form);
    });
  }

  function initEmailForm() {
    var form = qs('form[data-email-form]');
    if (!form) return;
    var emailInput = form.elements.email;
    var submitBtn = qs('[data-submit]', form);
    function updateSubmit() {
      var ready = emailInput.checkValidity();
      submitBtn.disabled = !ready;
    }
    emailInput.addEventListener('input', function () {
      updateSubmit();
      if (emailInput.getAttribute('aria-invalid') === 'true' && emailInput.checkValidity()) validate(emailInput);
    });
    updateSubmit();
    initValidatedForm(form, function () {
      var email = form.elements.email.value.trim();
      // TODO(api): ask the server whether the account exists.
      //   exists  -> connexion.html (an OTP is emailed)
      //   missing -> https://www.ndopify.com/joindre.html
      window.location.href = 'connexion.html?email=' + encodeURIComponent(email);
    });
  }

  function fmt(seconds) {
    var m = Math.floor(seconds / 60);
    var s = seconds % 60;
    return m + ':' + (s < 10 ? '0' : '') + s;
  }

  function initOtp() {
    var wrap = qs('[data-otp]');
    if (!wrap) return;
    var inputs = qsa('input', wrap);
    var timerEl = qs('[data-otp-timer]');
    var resend = qs('[data-otp-resend]');
    var expired = qs('[data-otp-expired]');
    var sent = qs('[data-otp-sent]');
    var emailEl = qs('[data-otp-email]');
    var error = qs('[data-otp-error]');
    var email = param('email');
    var remaining = OTP_TTL_SECONDS;
    var tick;

    if (emailEl) emailEl.textContent = email || 'votre adresse e-mail';

    function code() { return inputs.map(function (i) { return i.value; }).join(''); }

    var submitBtn = qs('[data-submit]');
    var submitHint = qs('#submit-hint');

    function updateSubmit() {
      var ready = code().length === OTP_LENGTH && remaining > 0;
      if (submitBtn) submitBtn.disabled = !ready;
      if (submitHint) {
        submitHint.textContent = remaining > 0 ? 'Saisissez les 6 chiffres du code pour vous connecter.' : 'Demandez un nouveau code pour vous connecter.';
      }
    }

    function render() {
      updateSubmit();
      if (timerEl) timerEl.textContent = remaining > 0 ? 'Le code expire dans ' + fmt(remaining) : 'Le code a expiré';
      if (expired) expired.hidden = remaining > 0;
    }

    function start() {
      clearInterval(tick);
      tick = setInterval(function () {
        if (remaining > 0) remaining -= 1;
        render();
      }, 1000);
      render();
    }

    function submit() {
      if (remaining <= 0) { render(); return; }
      // TODO(api): verify the code. On failure: show `error`, mark wrap[data-invalid], clear inputs.
      // The server applies rate limiting; surface its message in `error` when it answers 429.
      window.location.href = 'tableau-de-bord.html?statut=non-verifie';
    }

    inputs.forEach(function (input, i) {
      input.addEventListener('input', function () {
        input.value = input.value.replace(/\D/g, '').slice(0, 1);
        wrap.removeAttribute('data-invalid');
        if (error) error.textContent = '';
        if (input.value && inputs[i + 1]) inputs[i + 1].focus();
        updateSubmit();
      });
      input.addEventListener('keydown', function (e) {
        if (e.key === 'Backspace' && !input.value && inputs[i - 1]) inputs[i - 1].focus();
      });
      input.addEventListener('paste', function (e) {
        var digits = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, OTP_LENGTH);
        if (!digits) return;
        e.preventDefault();
        digits.split('').forEach(function (d, k) { if (inputs[k]) inputs[k].value = d; });
        (inputs[Math.min(digits.length, OTP_LENGTH - 1)]).focus();
        updateSubmit();
      });
    });

    if (resend) {
      resend.addEventListener('click', function () {
        // TODO(api): request a new code.
        remaining = OTP_TTL_SECONDS;
        inputs.forEach(function (i) { i.value = ''; });
        inputs[0].focus();
        if (sent) { sent.hidden = false; }
        render();
      });
    }

    var form = qs('form[data-otp-form]');
    if (form) form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (code().length < OTP_LENGTH) {
        if (error) error.textContent = 'Saisissez les 6 chiffres reçus par e-mail.';
        wrap.setAttribute('data-invalid', 'true');
        inputs[code().length].focus();
        return;
      }
      submit();
    });

    start();
  }

  function initDashboard() {
    var root = qs('[data-account-status]');
    if (!root) return;
    var status = accountStatus();
    qsa('[data-for-status]').forEach(function (el) {
      el.hidden = el.getAttribute('data-for-status') !== status;
    });
    var order = ['non-verifie', 'en-attente', 'verifie'];
    qsa('.tracker li').forEach(function (li, i) {
      var idx = order.indexOf(status);
      var done = i < idx || (idx === 2 && i === 2);
      li.setAttribute('data-done', String(done));
      var mark = qs('.tracker__mark', li);
      mark.className = 'tracker__mark icon ' + (done ? 'icon-check' : (i === idx ? 'icon-dot' : 'icon-ring'));
      if (i === idx && !done) li.setAttribute('aria-current', 'step'); else li.removeAttribute('aria-current');
    });
  }

  function initKyc() {
    var form = qs('form[data-kyc-form]');
    if (!form) return;
    form.setAttribute('novalidate', '');
    function checkFile(input) {
      var zone = input.closest('.upload');
      var err = qs('.field-error', zone);
      var preview = qs('.upload__preview', zone);
      var drawing = qs('svg', zone);
      var pick = qs('label.btn', zone);
      var file = input.files && input.files[0];
      var msg = '';
      if (!file) {
        if (input.required) msg = 'Ajoutez cette photo pour continuer.';
      } else if (ALLOWED_TYPES.indexOf(file.type) === -1) {
        msg = 'Format non accepté. Utilisez une photo au format JPG ou PNG.';
      } else if (file.size > MAX_FILE_BYTES) {
        msg = 'Ce fichier dépasse 5 Mo. Reprenez la photo avec une résolution plus basse.';
      }
      input.setAttribute('aria-invalid', String(!!msg));
      err.textContent = msg;
      if (preview.src) URL.revokeObjectURL(preview.src);
      if (file && !msg) {
        preview.src = URL.createObjectURL(file);
      } else {
        preview.removeAttribute('src');
      }
      preview.hidden = !(file && !msg);
      drawing.toggleAttribute('hidden', !preview.hidden);
      pick.textContent = file && !msg ? 'Changer le fichier' : 'Choisir le fichier';
      return !msg;
    }

    var submitBtn = qs('[data-submit]', form);

    function updateSubmit() {
      var ready = qsa('input[type="file"]', form).every(function (i) {
        return i.files && i.files.length && i.getAttribute('aria-invalid') !== 'true';
      }) && form.elements.consent.checked;
      submitBtn.disabled = !ready;
    }

    qsa('input[type="file"]', form).forEach(function (input) {
      input.addEventListener('change', function () { checkFile(input); updateSubmit(); });
    });
    form.elements.consent.addEventListener('change', function () {
      qs('[data-consent-error]', form).textContent = '';
      updateSubmit();
    });
    updateSubmit();

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var firstBad = null;
      qsa('input[type="file"]', form).forEach(function (input) {
        if (!checkFile(input) && !firstBad) firstBad = input;
      });
      var consent = form.elements.consent;
      if (consent && !consent.checked) {
        qs('[data-consent-error]', form).textContent = 'Confirmez que la carte est la vôtre et en cours de validité.';
        if (!firstBad) firstBad = consent;
      } else if (consent) {
        qs('[data-consent-error]', form).textContent = '';
      }
      if (firstBad) { firstBad.focus(); return; }
      // TODO(api): upload the files, then redirect.
      window.location.href = 'confirmation.html';
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initAccessGuard();
    initNavToggle();
    initEmailForm();
    initOtp();
    initDashboard();
    initKyc();
  });
})();
