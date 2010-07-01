(require 'docjure.core)
(use 'ring.adapter.jetty)
(run-jetty docjure.core/our-routes {:port 8080})
