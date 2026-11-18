## Agent skills

### Issue tracker

Issues live in GitHub Issues for this repo, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Before and after work

- **Before:** read `CONTEXT.md`, the section of `docs/features.md` for the area, and the ADRs it cites. Follow `CONTRIBUTING.md` (workflow and coding standards).
- **After (close the loop, same commit or PR):** update that `docs/features.md` section, add an ADR for any decision a reader would not guess, add new terms to `CONTEXT.md`, mark plans and handoffs done. Run the checks in `CONTRIBUTING.md`, then `graphify update .`.
