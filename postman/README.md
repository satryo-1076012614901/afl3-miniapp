# Postman

Import these files into Postman:

1. `mini-competition-manager.postman_collection.json`
2. `local.postman_environment.json`

Select **Mini Competition Manager - Local** before sending requests. For another deployment, duplicate the environment and change only `host`, for example `https://api.example.com/develop` without a trailing slash.

The collection covers all endpoints in the agreed API contract plus the repository's agreed `/system/status` readiness endpoint. It intentionally excludes implementation-only endpoints such as Spring Actuator. Run the create requests in order to populate `competitionId`, participant IDs, and match IDs automatically as collection variables. Competition and Participant requests will become runnable when their modules are integrated; the Match and System requests reflect the endpoints currently implemented on `main-farizhermawan`.
