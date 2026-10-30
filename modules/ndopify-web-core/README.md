# ndopify-web-core

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-core-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-core-jacoco.svg)

Infrastructure shared by the web apps (`ndopify-partner-central`, `ndopify-esignature`, `ndopify-web`): tenant
resolution from the request URL, tenant formatting of dates and amounts, and the REST interceptors / tenant client
used to call `ndopify-server`.

Used by all three web apps, each declaring the beans in its `config/WebCoreConfiguration`.

## Build

```bash
cd modules/ndopify-web-core
mvn verify
```
