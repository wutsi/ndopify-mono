// Ndopify — match proposal page (templates/match.html): listing.js opens and closes the popups (#answer, #reject,
// #visit) and shows the photos; this moves the focus into a popup when it opens, to its first choice, and keeps
// "Envoyer la demande" visually disabled until a day and a time are chosen. Without JavaScript the popups open with
// :target and their forms post. Nothing here answers: the answers are the forms' posts.
(function () {
  'use strict';

  var FIRST = { answer: '.answer-sheet button', reject: '[name="reason"]', visit: '[name="type"]' };

  function focusFirst(id) {
    var dialog = document.getElementById(id);
    var first = dialog && FIRST[id] && dialog.querySelector(FIRST[id]);
    if (first) first.focus();
  }

  // "Envoyer la demande" stays visually disabled until a day and a time are chosen. The form still posts (no
  // preventDefault): without JavaScript the browser enforces the required day and time.
  function initVisit() {
    var form = document.querySelector('#visit [data-visit-request-form]');
    if (!form) return;
    var submit = form.querySelector('[type="submit"]');
    function refresh() {
      var day = form.querySelector('[name="visitDate"]:checked');
      var time = form.querySelector('#visit-time');
      submit.setAttribute('aria-disabled', String(!(day && time && time.value)));
    }
    form.addEventListener('input', refresh);
    form.addEventListener('change', refresh);
    refresh();
  }

  document.addEventListener('click', function (e) {
    var trigger = e.target.closest('[data-modal-open]');
    if (!trigger) return;
    var id = trigger.getAttribute('href').slice(1);
    // After listing.js has opened it (its handler runs first: it was added first)
    setTimeout(function () { focusFirst(id); }, 0);
  });

  document.addEventListener('DOMContentLoaded', function () {
    initVisit();
    if (location.hash) focusFirst(location.hash.slice(1));
  });
})();
