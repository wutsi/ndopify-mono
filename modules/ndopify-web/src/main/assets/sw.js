"use strict";
const VERSION = "__VERSION__";
const PREFIX = "nweb-offline-";
const CACHE = PREFIX + VERSION;
const PRECACHE = ["/offline.html", "/assets/images/logo.png"];

self.addEventListener("install", (event) => {
    event.waitUntil(
        caches.open(CACHE)
            .then((cache) => cache.addAll(PRECACHE.map((url) => new Request(url, { cache: "reload" }))))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        caches.keys()
            // Only this app's own caches: another app on the same origin keeps its caches.
            .then((keys) => Promise.all(
                keys.filter((k) => k.startsWith(PREFIX) && k !== CACHE).map((k) => caches.delete(k))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener("fetch", (event) => {
    const request = event.request;
    if (request.mode === "navigate") {
        event.respondWith(
            fetch(request).catch(() =>
                caches.match("/offline.html").then((hit) => hit || Response.error())
            )
        );
        return;
    }
    const url = new URL(request.url);
    if (url.origin === self.location.origin && PRECACHE.includes(url.pathname)) {
        event.respondWith(caches.match(url.pathname).then((hit) => hit || fetch(request)));
    }
});
