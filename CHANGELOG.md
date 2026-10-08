# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-10-08

User stories: code-corhuila/barber-saas-docs#4, code-corhuila/barber-saas-docs#59

### Added

- **domain:** add the business rule violation raised by the domain
- **domain:** add the time slot value object and the sunday-first weekday
- **domain:** add the weekly schedule without overlapping blocks
- **domain:** add the schedule exception, a day off or special hours
- **domain:** compute free slots from every block of the day minus bookings
- **application:** declare the caller, its role, its tenant and its credential
- **application:** declare what schedule asks barbershop-api
- **application:** declare the weekly schedule use cases and repository
- **application:** read and replace a barber's weekly schedule
- **application:** add the page and the result of an idempotent creation
- **application:** hash requests with their tenant for idempotency keys
- **application:** declare the schedule exception use cases and repository
- **application:** register, list and delete schedule exceptions
- **application:** declare what schedule asks appointment-api
- **application:** declare the availability use case
- **application:** compute a barber's free slots for a service and date
- **persistence:** generate identifiers in the service
- **persistence:** add in-memory schedules and exceptions for runs without a database
- **persistence:** page with bound values and store idempotency keys in jdbc
- **persistence:** replace a weekly schedule in one transaction
- **persistence:** store schedule exceptions and their keys with jdbc
- **application:** declare the failure of another domain's api
- **http:** call other apis with timeouts, the caller's token and the correlation id
- **http:** ask barbershop-api for barbers, services and the time zone
- **http:** ask appointment-api for the time a barber has booked
- **http:** add the shared error envelope
- **http:** turn every error, including a failed dependency, into one status code
- **http:** reuse or create the correlation id and log one line per request
- **http:** answer the liveness probe without a token
- **security:** verify rs256 tokens and keep the token to pass on
- **http:** require a token on every api route
- **http:** read bodies strictly, with hh:mm times and indexed slot errors
- **http:** validate paging and the idempotency key
- **http:** add the response views with hh:mm times and the page envelope
- **app:** add the spring boot entry point
- **app:** wire every port to its adapter with explicit pool limits
- **app:** set timeouts, graceful shutdown, json logs and the other apis
- **http:** expose a barber's weekly schedule
- **http:** expose schedule exceptions
- **http:** expose a barber's availability
- **deploy:** build the image and compose the service on the shared instance
- **deploy:** ask appointment-api for booked time on the platform
- **application:** ask busy slots for the barbershop of the caller's token
- **http:** read busy slots with this service's own token
- **app:** give the appointment client this service's token
- **deploy:** give schedule its own service token

### Fixed

- **http:** answer 503 when another service does not answer

### Documentation

- **readme:** explain the service, how to run and test it, and what is missing
- **readme:** drop bookings from what is missing
- **readme:** explain busy slots with the service token and drop oq-09
- **readme:** point the header to Barber Saas and barber-saas-docs

### Tests

- **ci:** build and test every pull request with java 21
- **domain:** specify time slots, the weekly schedule, exceptions and availability
- **application:** specify the weekly schedule with fake ports
- **application:** specify schedule exceptions with fake ports
- **application:** specify availability with bookings, exceptions and time zone
- **persistence:** specify the in-memory schedule and exception repositories
- **persistence:** check the jdbc repositories against a migrated database
- **http:** specify the clients of barbershop-api and appointment-api
- **security:** specify which tokens are accepted and what they carry
- **http:** specify the body reader for slots, times and dates
- **app:** check health, the envelope and the correlation id over http
- **http:** specify schedules, exceptions and availability over http
- **availability:** specify busy slots read with schedule's own token for every role

### Maintenance

- **build:** ignore build output, ide files, env files and keys
- **github:** add the pull request template
- **github:** track the story environment on the board
- **build:** add the maven parent on spring boot 3.5 and java 21
- **build:** add the core module without any framework dependency
- **build:** add the adapters module with spring web and jdbc
- **build:** add the app module that composes the service
- **env:** list every variable without real values
- use the new repository name barber-saas-infra-postgres

[2.0.0]: https://github.com/code-corhuila/barber-saas-schedule-api/releases/tag/v2.0.0
