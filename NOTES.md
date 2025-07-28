This is written in Clojure.  You'll need to install clojure and Java if you don't have it already installed.
It shouldn't be too picky about java version.

./bin/start.sh will start it and background it.

clj -M:dev:repl will start a development repl, after which you can load and switch
to the dev namespace:

```
20:39:36 0 jfelice@crunch messaging-service (main)
ᐅ clj -M:dev:repl
nREPL server started on port 33043 on host localhost - nrepl://localhost:33043
nREPL 1.3.1
Clojure 1.12.1
OpenJDK 64-Bit Server VM 21.0.7+6-nixos
Interrupt: Control+C
Exit:      Control+D or (exit) or (quit)
user=> (require 'dev)
nil
user=> (in-ns 'dev)
#namespace[dev]
dev=>
```

At this prompt, `(refresh)` will reload all code, and `(run-tests)` will run tests.
Usually I just type this all on one line so that <UpArrow><Enter> loads and runs
everything.


Files:

```
dev/dev.clj                                  - Development tools
src/messaging_service/message.clj            - Message format definition, normalizer, etc.*
src/messaging_service/provider.clj           - Plumbing for pluggable providers.
src/messaging_service/provider/sendgrid.clj  - SendGrid fake provider
src/messaging_service/provider/twilio.clj    - Twilio fake provider
src/messaging_service/db.clj                 - Database layer.
src/messaging_service/main.clj               - Entry point.
src/messaging_service/handler.clj            - The web handler that does all the work.
test/messaging_service/handler_test.clj      - Unit tests for endpoints.
```

