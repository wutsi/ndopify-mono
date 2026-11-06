# Partner Central — One-Call Current Agent — Design

## Intent

Every signed-in Partner Central page resolves the caller's agent with two HTTP calls:

1. `GET /v1/agents?party-id={partyId}&limit=1` — search by the token's party id;
2. `GET /v1/agents/{id}` — fetch the full agent.

The first call is not cheap because the agent's data is missing. `AgentEndpoints.search` already loads the full
`AgentEntity` and its party in one query (`AgentRepository.findAll` has `@EntityGraph(attributePaths = ["party"])`),
then maps it down to `AgentSummary`. The second call exists only because the search endpoint's contract is a summary.

The change adds `GET /v1/agents/me`: the server resolves the caller's party from its access token and returns the
full agent as a `GetAgentResponse`. Partner Central's `CurrentAgent` becomes one call, and the agent identity is no
longer derived from a client-supplied `party-id`.

## Goals

- One HTTP call to resolve the signed-in agent, on every signed-in page.
- Keep returning the full `Agent` (`biography`, `version`, neighbourhoods, …): the profile pages need it.
- Resolve the agent server-side from the token, never from a request parameter.
- Keep today's behaviour for "not an agent": Partner Central signs the caller out.
- No DTO change, no database migration, no caching.

## Non-goals

- Removing the `CurrentAgent` lookup entirely (e.g. caching it in the session).
- Adding an `agentId` claim to the JWT. The token still carries `partyId`; the server resolves the membership.
- Changing `GET /v1/agents` (search) or its `AgentSummary` contract.
- Reducing the other home-page calls; this change only removes one round trip from every signed-in page.

## Constraints

- `GET /v1/agents/me` requires a JWT (it is not added to `PublicEndpoints`) and is tenant-scoped by the token.
- It returns `GetAgentResponse`, as requested.
- 404 `AGENT_NOT_FOUND` when the caller has no membership/party in the current tenant, or its party isn't an agent.
- Partner Central maps exactly 404 to `null`; any other status, timeout or connection failure propagates to
  `HttpClientErrorHandler` (401 signs out, 5xx is an error). A missing endpoint must not be mistaken for "not an agent".
- Deploy ndopify-server before Partner Central: an old server routes `/v1/agents/me` to `/{id}` and answers 400.
- Kotlin/ktlint; `kotlin.test` assertions in the server module; Mockito/`MockRestServiceServer` in Partner Central.
- Code and docs in English; user-facing text stays French.

## Approaches considered

1. **`GET /v1/agents/me` (chosen).** The server uses `CallerService.findPartyIdOrNull(principal)`, then
   `AgentService.findByPartyIdOrNull(partyId)` and `AgentMapper.toAgent`. One round trip on every page, identity is
   server-authoritative, the search endpoint keeps its summary contract. Cost: a new endpoint and a small
   user/membership lookup on the server, which is local and replaces the same lookup the client used to trigger.
2. **Return full `Agent`s from the search.** `SearchAgentResponse.agents` would change to `List<Agent>` (a
   wire-compatible superset) or `AgentSummary` would gain `biography`. Smallest diff, no new endpoint. Rejected: a
   generic search becomes detail-heavy, the summary/detail distinction disappears, and mapping full agents triggers
   the neighbourhood `SUBSELECT` for every search page.
3. **Put `agentId` in the JWT, like `partyId`.** `CurrentAgent` would read the claim and call
   `GET /v1/agents/{id}`. One call, but it touches the shared token path used by every app, makes the claim stale
   when a party becomes an agent after sign-in, and saves no round trip compared with option 1.

## Decisions

- Endpoint: `GET /v1/agents/me` in `AgentEndpoints`, returning `GetAgentResponse`. Add `CallerService` to the
  endpoint's constructor. No `AgentAccessService`: there is no entity-level access rule beyond "the caller's own
  agent"; the endpoint follows `UserEndpoints`' pattern of resolving the caller from the token.
- Partner Central client: `NdAgentClient.me(): Agent?` maps 404 to `null`, mirroring `NdUserClient.get()`.
- `CurrentAgent` drops its `CurrentParty` dependency: `get()` is `agentClient.me()`.
- `NdAgentClient.findByPartyId` is removed once `CurrentAgent` stops using it.
- `CurrentParty.id()` (the token's `partyId`, or a user lookup) is removed once `CurrentAgent` stops using it;
  `CurrentParty.get()` stays for `KycController`.
- Tests: a dedicated `GetAgentMeEndpointTest` with `T_USER`/`T_MEMBERSHIP` rows; `NdAgentClientTest` gains `me()`;
  `CurrentAgentTest` switches to `me()`.

## What is left to the builder

Nothing open. Names, statuses, fixtures and commands are pinned in the implementation plan.
