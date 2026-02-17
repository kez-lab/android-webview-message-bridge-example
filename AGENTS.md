# Repository Agent Rules

## PR Workflow Memory

- Always prepare PR body in a markdown file first.
- Use `docs/pr/templates/pr-body.template.md` as the starting template.
- Write draft bodies under `docs/pr/drafts/` (for example: `docs/pr/drafts/pr-12-body.md`).
- Create or update PR with `--body-file` only.
  - Create: `gh pr create --base main --head <branch> --title "<title>" --body-file docs/pr/drafts/<file>.md`
  - Edit: `gh pr edit <number> --body-file docs/pr/drafts/<file>.md`
- Do not use inline multiline `--body` for PR descriptions.
