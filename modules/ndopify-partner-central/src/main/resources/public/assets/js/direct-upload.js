// Uploads one file straight to the storage, for a form whose action also answers "<action>/presign" and
// "<action>/attach" (JSON). Resolves true once attached, false when the direct path can't run (local storage,
// no bucket CORS, expired URL, any error): the caller then posts the file to the form as usual.
// A redirected answer is a failure too: an expired session is redirected to the login page, whose 200 `fetch` follows.
// So is a stalled call: each one is aborted after its timeout, so the caller's fallback always runs.
(function () {
    var CALL_TIMEOUT_MS = 30 * 1000;
    var PUT_TIMEOUT_MS = 3 * 60 * 1000; // up to 5 Mo on a slow mobile network

    function timed(url, options, timeout) {
        if (typeof AbortController !== "function") return fetch(url, options);
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, timeout);
        options.signal = controller.signal;
        return fetch(url, options).then(function (response) {
            clearTimeout(timer);
            return response;
        }, function (error) {
            clearTimeout(timer);
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
        }, CALL_TIMEOUT_MS).then(function (response) {
            return ok(response) ? response.json() : null;
        }).then(function (presigned) {
            if (!presigned || !presigned.url) return false;
            return timed(presigned.url, {
                method: "PUT",
                headers: { "Content-Type": file.type },
                body: file
            }, PUT_TIMEOUT_MS).then(function (put) {
                if (!put.ok) return false;
                return timed(action + "/attach", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ path: presigned.path })
                }, CALL_TIMEOUT_MS).then(function (attached) {
                    return ok(attached);
                });
            });
        }).catch(function () {
            return false;
        });
    }

    window.NdDirectUpload = { upload: upload };
})();
