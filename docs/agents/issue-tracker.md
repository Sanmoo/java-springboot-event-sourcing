# Issue tracker: GitHub

Issues and specs live in GitHub Issues for `Sanmoo/java-springboot-event-sourcing`.
Use the `gh` CLI for all operations.

## Conventions

- Create: `gh issue create --title "..." --body "..."`. Use a heredoc for multiline bodies.
- Read: `gh issue view <number> --json number,title,body,labels,comments`.
- List: `gh issue list --state open --json number,title,body,labels,comments`, with appropriate filters.
- Comment: `gh issue comment <number> --body "..."`.
- Add or remove labels: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- Close: `gh issue close <number> --comment "..."`.
- Create a sub-issue: `gh issue create --parent <parent> ...`, or link it with
  `gh issue edit <parent> --add-sub-issue <child>` (gh 2.94+). If sub-issues
  are unavailable, put `Part of #<parent>` at the top of the child body.

## Pull requests as a triage surface

**PRs as a request surface: no.** Set this to `yes` if external PRs should
enter the triage queue.

## Skill operations

- When asked to publish to the issue tracker, create a GitHub issue.
- When asked to fetch a ticket, read the issue as described above.

## Wayfinding operations

`/wayfinder` uses a map issue labelled `wayfinder:map` and child issues
labelled `wayfinder:<type>` (`research`, `prototype`, `grilling`, or `task`).
Link children as sub-issues or use a task list and `Part of #<map>` fallback.

Use GitHub's native issue dependencies for blockers. Add one with:

`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`

The blocker ID is its numeric database ID, obtainable with
`gh api repos/<owner>/<repo>/issues/<number> --jq .id`. If dependencies are
unavailable, add `Blocked by: #<number>` to the child body. A ticket is
unblocked when all its blockers are closed.

To choose the next ticket, list the map's open children, exclude those with
open blockers or an assignee, and choose the first in map order. Claim it with
`gh issue edit <number> --add-assignee @me`. On resolution, comment with the
answer, close the issue, and append a context pointer to the map.
