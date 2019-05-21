A GreaseMonkey/TamperMonkey intercepts XHR requests on https://robinhood.com, sends data to this application.
That data will be saved to files.

```
// ==UserScript==
// @name         robinhood
// @namespace    http://tampermonkey.net/
// @version      0.1
// @description  try to take over the world!
// @author       You
// @match        https://robinhood.com/*
// @grant        GM_addStyle
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function() {
    'use strict';

    (function(open) {
        XMLHttpRequest.prototype.open = function() {
            this.addEventListener('readystatechange', function() {
                if (this.readyState === 4 && this.status === 200 && this.__sentry_xhr__ && this.__sentry_xhr__.method === 'GET') {
                    const xhr = this.__sentry_xhr__;
                    if (xhr.url.indexOf('https://api.robinhood.com/marketdata/quotes') === 0) {
                        const data = this.response;
                        GM_xmlhttpRequest({
                            data: data,
                            method: 'POST',
                            url: 'http://localhost:4567/quotes'
                        });
                    }
                }
            }, false);
            open.apply(this, arguments);
        };
    })(XMLHttpRequest.prototype.open);
})();
``` 