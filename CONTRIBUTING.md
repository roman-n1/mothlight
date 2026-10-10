# Contributing

Keep core independent of UI and transports. Add regression tests before changing
recording, privacy, lifecycle or transport behavior. Use deterministic inputs and
virtual coroutine time; do not use sleeps in tests.

All user-facing library strings need English and Russian resources. Do not place
credentials, real traffic, internal hostnames or application source history in
fixtures. Run the resource checker and targeted tests from README.

The public API is experimental during 0.1.x. Explain API changes and update examples.
Contributions are provided under Apache-2.0.
