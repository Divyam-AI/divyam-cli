# Divyam dashboards (Superset)

Three Superset dashboards, all hosted under `https://dashboard.divyam.ai/`. Every one exposes a
**service account filter**, so a useful link is the dashboard URL plus the service account the
user should filter on.

| Dashboard | URL | Use it after… |
|-----------|-----|---------------|
| Selector Training | `https://dashboard.divyam.ai/superset/dashboard/training-dashboard/` | creating a selector (watch training progress) |
| A/B Traffic | `https://dashboard.divyam.ai/superset/dashboard/ab_perf_dashboard_client_side/` | moving a selector to prod or changing traffic allocation |
| Raw Logs | `https://dashboard.divyam.ai/superset/dashboard/raw_logs` | debugging individual requests |

## What to emit, and when

Include the link as a plain line of output — not as an offer ("want me to…"). The service account
is named alongside it since the dashboards land unfiltered.

- **After `selector create`** → the **Selector Training** link:
  > Training the selector. Watch it here (filter to service account `<sa-name>` / `<sa-id>`):
  > https://dashboard.divyam.ai/superset/dashboard/training-dashboard/
- **After `selector update --to-prod` or a traffic reallocation** → the **A/B Traffic** link,
  same SA-filter note.
- **When the user is debugging a request/response** → the **Raw Logs** link.

## Pre-filtered deep links (optional upgrade)

Superset can deep-link straight to a service-account-filtered view via the dashboard's
**native filter** — a URL like `.../training-dashboard/?native_filters=<rison>` — but the encoding
must reference that filter's generated component id (`NATIVE_FILTER-…`), which isn't knowable from
the CLI or this repo. Until a real filtered URL is captured, hand out the base link plus the SA to
select.

To enable true deep-links later: in Superset, open a dashboard, set the **service account** filter
to one value, copy the full URL from the browser, and paste it below. Then this skill can turn it
into a template by swapping the SA value.

```
# TEMPLATE SLOT — paste a working service-account-filtered URL here, one per dashboard:
# Selector Training: <paste>
# A/B Traffic:       <paste>
# Raw Logs:          <paste>
```
