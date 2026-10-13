# Postman

Postman resources use the Postman v3 local-file format. The collection is split into readable YAML files under:

- `collections/Mini Competition Manager API/`
- `environments/Mini Competition Manager - Local.environment.yaml`

## Open in Postman

1. Open the repository root in Postman **Local View**. The root `.postman/resources.yaml` binds the workspace and automatically registers resources inside the `postman/` directory.
2. Select **Mini Competition Manager - Local** as the active environment.
3. Start the backend, then send **System → System Status** to verify the selected `host`.

For another deployment, duplicate the environment and change only `host`, for example `https://api.example.com/develop` without a trailing slash. Do not edit each request URL.

## Running the API flow

`API Catalog` contains exactly one reusable request for every agreed endpoint plus `/system/status`. It intentionally excludes implementation-only endpoints such as Spring Actuator.

Run the create requests in order: create a competition, create four participants, then generate the bracket. Their after-response scripts populate `competitionId`, participant IDs, and match IDs as collection variables for subsequent requests.

## End-to-end test

The collection includes these independent scenarios:

- `E2E Individual`: complete four-player lifecycle.
- `E2E Team`: complete four-team lifecycle including team members.
- `E2E Multiple Byes`: verifies a five-participant bracket with two non-consecutive byes.
- `E2E Sad Paths`: invalid winner and premature-final errors.

Start the backend and run one self-cleaning scenario with Postman CLI:

```bash
postman collection run "postman/collections/Mini Competition Manager API" \
  --environment "postman/environments/Mini Competition Manager - Local.environment.yaml" \
  -i "E2E Individual" \
  --no-report-events
```

Replace the `-i` value to run another scenario. Do not add `--bail`: the runner must continue to the teardown requests even after an assertion fails. Failed `pm.test` assertions still make the final command exit non-zero.

Cleanup uses the public API rather than direct SQL. Every scenario creates its own competition, then resets/deletes it at the end. A hard process or network failure can still interrupt teardown, but the scenario-specific data names prevent leftovers from blocking the next run.
