# eddy

Jetty11+ server library (client support might eventually be added later).

* builds on protocols for request/response handling (no ring by default, fast)
* can run Handlers via an [exoscale/interceptor](https://github.com/exoscale/interceptor) chain, meaning you can skip any request/response handling phase, at "config time" or "request time", less is more
* provides an interceptor chain for RING (v1) handlers
* async input/output via CompletableFuture
* NIO reading/writing
* sync or async handler base
* optional support for HTTP-2
* quite minimal codebase

[wip]

## Documentation



## Installation

eddy is [available on Clojars](https://clojars.org/s-exp/eddy).

## License

Copyright © 2021 [s-exp](httpf://s-exp.com)

Eclipse Public License - https://www.eclipse.org/legal/epl-v10.html

## Inspired from code of ring-clojure

Copyright © 2009-2021 Mark McGranaghan, James Reeves & contributors.

[MIT license](https://github.com/ring-clojure/ring/blob/master/LICENSE)
