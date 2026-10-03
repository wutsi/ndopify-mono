// Ndopify — shared behavior: mobile nav, FAQ accordion, multi-step wizard, field masking.
(function () {
  'use strict';

  function initNavToggle() {
    var toggle = document.querySelector('.nav-toggle');
    var list = document.querySelector('.site-nav__list');
    if (!toggle || !list) return;
    toggle.addEventListener('click', function () {
      var isOpen = list.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(isOpen));
    });
    list.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        list.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.focus();
      }
    });
  }

  function initFaq() {
    var triggers = document.querySelectorAll('.faq-trigger');
    triggers.forEach(function (trigger) {
      trigger.addEventListener('click', function () {
        var expanded = trigger.getAttribute('aria-expanded') === 'true';
        var panelId = trigger.getAttribute('aria-controls');
        var panel = document.getElementById(panelId);
        trigger.setAttribute('aria-expanded', String(!expanded));
        if (panel) panel.hidden = expanded;
      });
    });
  }

  function initMasking() {
    if (window.AutoNumeric) {
      document.querySelectorAll('[data-amount]').forEach(function (el) {
        new window.AutoNumeric(el, {
          digitGroupSeparator: ' ',
          decimalPlaces: 0,
          minimumValue: '0',
          suffixText: ' FCFA'
        });
      });
    }
    if (window.intlTelInput) {
      document.querySelectorAll('[data-phone]').forEach(function (el) {
        window.intlTelInput(el, {
          initialCountry: 'cm',
          preferredCountries: ['cm', 'ga', 'cg', 'td', 'cf', 'gq'],
          separateDialCode: true
        });
      });
    }
  }

  function fieldOf(control) {
    return control.closest('.field');
  }

  function errorElementOf(control) {
    var field = fieldOf(control);
    return field ? field.querySelector('.field-error') : null;
  }

  function validateControl(control) {
    if (!control.willValidate) return true;
    var errorEl = errorElementOf(control);
    var valid = control.checkValidity();
    control.setAttribute('aria-invalid', String(!valid));
    if (errorEl) {
      errorEl.textContent = valid ? '' : (control.validationMessage || 'Ce champ est requis.');
    }
    return valid;
  }

  function initWizard(form) {
    var steps = Array.prototype.slice.call(form.querySelectorAll('fieldset.step'));
    if (!steps.length) return;
    var progressFill = document.querySelector('[data-progress-fill]');
    var progressLabel = document.querySelector('[data-progress-label]');
    var progressBar = document.querySelector('[data-progress-bar]');
    var totalDataSteps = steps.filter(function (s) { return !s.hasAttribute('data-confirmation'); }).length;
    var current = 0;

    function requiredControlsIn(step) {
      return Array.prototype.slice.call(step.querySelectorAll('input[required], select[required]'));
    }

    function stepIsValid(step) {
      var controls = requiredControlsIn(step);
      var allValid = true;
      controls.forEach(function (control) {
        if (!validateControl(control)) allValid = false;
      });
      return allValid;
    }

    function updateProgress() {
      var stepNumber = Math.min(current + 1, totalDataSteps);
      var pct = Math.round((stepNumber / totalDataSteps) * 100);
      if (progressFill) progressFill.style.transform = 'scaleX(' + (pct / 100) + ')';
      if (progressBar) {
        progressBar.setAttribute('aria-valuenow', String(stepNumber));
        progressBar.setAttribute('aria-valuetext', 'Étape ' + stepNumber + ' sur ' + totalDataSteps);
      }
      if (progressLabel) progressLabel.textContent = 'Étape ' + stepNumber + ' sur ' + totalDataSteps;
    }

    function showStep(index) {
      steps.forEach(function (step, i) {
        step.hidden = i !== index;
      });
      current = index;
      updateProgress();
      var heading = steps[index].querySelector('legend');
      if (heading) heading.setAttribute('tabindex', '-1');
      if (heading && typeof heading.focus === 'function') heading.focus();
      var progressWrap = document.querySelector('.progress');
      if (progressWrap && !steps[index].hasAttribute('data-confirmation')) {
        progressWrap.hidden = false;
      } else if (progressWrap) {
        progressWrap.hidden = true;
      }
    }

    form.addEventListener('click', function (e) {
      var next = e.target.closest('[data-action="next"]');
      var prev = e.target.closest('[data-action="prev"]');
      if (next) {
        e.preventDefault();
        if (stepIsValid(steps[current])) {
          var categoryToggle = steps[current].querySelector('[data-category-toggle]');
          if (categoryToggle) applyCategoryToggle(form);
          if (current < steps.length - 1) showStep(current + 1);
        } else {
          var firstInvalid = steps[current].querySelector('[aria-invalid="true"]');
          if (firstInvalid) firstInvalid.focus();
        }
      }
      if (prev) {
        e.preventDefault();
        if (current > 0) showStep(current - 1);
      }
    });

    form.addEventListener('blur', function (e) {
      if (e.target.matches('input, select')) validateControl(e.target);
    }, true);

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (stepIsValid(steps[current])) {
        showStep(steps.length - 1);
      }
    });

    showStep(0);
  }

  function applyCategoryToggle(form) {
    var selected = form.querySelector('input[name="categorie"]:checked');
    if (!selected) return;
    var value = selected.value;
    form.querySelectorAll('[data-show-for]').forEach(function (group) {
      var show = group.getAttribute('data-show-for') === value;
      group.hidden = !show;
      group.querySelectorAll('input, select').forEach(function (control) {
        if (show) {
          if (control.dataset.requiredWhenVisible !== undefined) control.required = true;
        } else {
          control.required = false;
          control.value = '';
        }
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initNavToggle();
    initFaq();
    initMasking();
    var wizard = document.querySelector('form.wizard');
    if (wizard) initWizard(wizard);
  });
})();
