// Uploads one file straight to the storage, for a form whose action also answers "<action>/presign" and
// "<action>/attach" (JSON). Resolves true once attached, false when the direct path can't run (local storage,
// no bucket CORS, expired URL, any error): the caller then posts the file to the form as usual.
// A redirected answer is a failure too: an expired session is redirected to the login page, whose 200 `fetch` follows.
// So is a stalled call: each one is aborted after its timeout, so the caller's fallback always runs.
(function () {
    var CALL_TIMEOUT_MS = 30 * 1000;
    var PUT_MIN_TIMEOUT_MS = 3 * 60 * 1000;
    var PUT_MIN_BYTES_PER_MS = 10; // ~10 Ko/s: a slow mobile network still gets the time to send a 5 Mo photo

    // fetch, then read(response), aborted when both haven't settled within timeout (the body can stall too)
    function timed(url, options, timeout, read) {
        var controller = typeof AbortController === "function" ? new AbortController() : null;
        var timer = controller && setTimeout(function () { controller.abort(); }, timeout);
        if (controller) options.signal = controller.signal;
        function done() {
            if (timer) clearTimeout(timer);
        }
        return fetch(url, options).then(read).then(function (result) {
            done();
            return result;
        }, function (error) {
            done();
            throw error;
        });
    }

    function ok(response) {
        return response.ok && !response.redirected;
    }

    function upload(action, file) {
        return timed(action + "/presign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ contentType: file.type })
        }, CALL_TIMEOUT_MS, function (response) {
            return ok(response) ? response.json() : null;
        }).then(function (presigned) {
            if (!presigned || !presigned.url) return false;
            return timed(presigned.url, {
                method: "PUT",
                headers: { "Content-Type": file.type },
                body: file
            }, Math.max(PUT_MIN_TIMEOUT_MS, file.size / PUT_MIN_BYTES_PER_MS), function (put) {
                return put.ok;
            }).then(function (stored) {
                if (!stored) return false;
                return timed(action + "/attach", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ path: presigned.path })
                }, CALL_TIMEOUT_MS, function (attached) {
                    return ok(attached);
                });
            });
        }).catch(function () {
            return false;
        });
    }

    window.NdDirectUpload = { upload: upload };
})();
