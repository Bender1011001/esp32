---
description: Iterative project critique and fix loop until no major issues remain
---

# Critique and Fix Workflow

A self-correcting loop that examines code as a ruthless critic, fixes issues systematically, and iterates until the codebase is clean.

---

## How This Works

You alternate between two mindsets:
1. **Critic**: Find everything wrong. Be adversarial. Assume bugs exist.
2. **Fixer**: Solve problems minimally and correctly. Don't gold-plate.

The loop continues until the Critic runs out of real issues to complain about.

---

## Phase 1: Critic Mode

**Mindset**: You're a senior engineer who's seen every way code can fail. You're not here to be nice—you're here to find problems before production does.

### How to Actually Do This

Don't just skim. Follow data and control flow:
- Trace user inputs from entry to storage
- Follow error paths—what happens when things fail?
- Check boundaries—what if that array is empty? What if that response is null?
- Look for assumptions that aren't validated

### What to Look For

**Functional Issues** (Things that break)
- Unhandled edge cases (empty inputs, null returns, network failures)
- Race conditions or async timing issues
- State that can get out of sync
- Resource leaks (unclosed connections, listeners not removed)
- Missing error boundaries

**Structural Issues** (Things that rot)
- Functions doing too much (>40 lines = suspect)
- Duplicated logic (three or more instances = abstract it)
- Circular or tangled dependencies
- Mixed abstraction levels in same function
- Dead code paths

**Security Issues** (Things that explode)
- Hardcoded secrets or keys
- User input used without validation
- Sensitive data in logs or error messages
- Missing auth checks on protected paths

**UX Issues** (Things that frustrate)
- Missing loading/pending states
- Generic or unhelpful error messages
- Inconsistent behavior patterns
- Silent failures

**Documentation Issues** (Things that confuse)
- CONTEXT.md missing or lying
- Public APIs without usage examples
- Comments that contradict code

---

## Phase 2: Triage

Categorize every issue found:

| Severity | Definition | Action |
|----------|------------|--------|
| **CRITICAL** | Crashes, data loss, security holes, blocks core functionality | Fix immediately |
| **MAJOR** | Significant bugs, bad UX, code that will cause problems soon | Fix this round |
| **MINOR** | Polish, style, small improvements | Log for later |
| **WONTFIX** | Intentional trade-off or out of scope | Document why |

**Rule**: Only work on CRITICAL and MAJOR this round. Minor items accumulate for a polish pass later.

---

## Phase 3: Fixer Mode

**Mindset**: You're a surgeon. Get in, fix the specific problem, get out. Don't refactor the whole file because you're already in there.

### For Each Issue

1. **Understand**: What's the actual root cause? (Not the symptom)
2. **Scope**: What's the minimum change that fixes it?
3. **Implement**: Make the fix
4. **Verify**: Confirm the fix works AND didn't break adjacent code

### Fix Quality Checklist
- [ ] Fix addresses root cause, not just symptom
- [ ] No unrelated changes snuck in
- [ ] Error cases handled, not just happy path
- [ ] Consistent with surrounding code style

---

## Phase 4: Verify

Run the project's standard validation:

```bash
# Build/compile passes
# Tests pass (if they exist)
# Linter passes (if configured)
# App runs and core flows work
```

Adapt to your project. The point: prove you didn't break anything.

---

## Phase 5: Loop Decision

**Re-enter Critic Mode** and examine again. Be suspicious of your own fixes.

### Continue if:
- Any CRITICAL issues remain
- Any MAJOR issues remain
- New issues introduced by fixes

### Exit if:
- Only MINOR items remain
- Critic is reduced to style preferences
- Remaining items are documented trade-offs

---

## Output Format

Use this structure for each round:

```markdown
## Critique Round [N]

### CRITICAL
1. **[path/file.ext:LINE]** Short description
   - **What's wrong**: Concrete explanation
   - **Why it matters**: Impact if unfixed  
   - **Fix**: Specific solution

### MAJOR  
1. **[path/file.ext:LINE]** Short description
   - **What's wrong**: ...
   - **Why it matters**: ...
   - **Fix**: ...

### MINOR (log only)
- Item 1
- Item 2

### WONTFIX
- Item: Reason it's intentional

---
**Status**: CONTINUE | COMPLETE
**Next action**: [What happens next]
```

---

## Anti-Patterns to Avoid

- **Endless loops**: Set a max of 5 rounds. If still finding CRITICAL issues after 5, the architecture needs rethinking, not more patches.
- **Scope creep**: "While I'm here..." is how simple fixes become rewrites.
- **Gold-plating**: Perfect is the enemy of shipped.
- **Phantom issues**: If you can't point to a specific line and explain the concrete failure mode, it's not a real issue.

---

## When to Use This

- Before any major merge or release
- After significant refactoring
- When inheriting unfamiliar code
- Periodic hygiene on active projects
