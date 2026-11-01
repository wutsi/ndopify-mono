// Ndopify — match proposal page (templates/match.html): listing.js opens and closes the popups (#answer, #reject) and
// shows the photos; this only moves the focus into a popup when it opens, to its first choice. Without JavaScript the
// popups open with :target and their forms post. Nothing here answers: the answers are the forms' posts.
(function () {
  'use strict';

  var FIRST = { answer: '.answer-sheet button', reject: '[name="reason"]' };

  function focusFirst(id) {
    var dialog = document.getElementById(id);
    var first = dialog && FIRST[id] && dialog.querySelector(FIRST[id]);
    if (first) first.focus();
  }

  document.addEventListener('click', function (e) {
    var trigger = e.target.closest('[data-modal-open]');
    if (!trigger) return;
    var id = trigger.getAttribute('href').slice(1);
    // After listing.js has opened it (its handler runs first: it was added first)
    setTimeout(function () { focusFirst(id); }, 0);
  });

  document.addEventListener('DOMContentLoaded', function () {
    if (location.hash) focusFirst(location.hash.slice(1));
  });
})();
