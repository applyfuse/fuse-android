# FUSE Android — Pull Request template

## What does this PR do?

<!-- Brief description of the change -->

## Type of change

- [ ] New feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Documentation

## FUSE checklist

- [ ] Reducer is pure (no coroutines, no side effects)
- [ ] New actions added to the sealed class
- [ ] Reducer tests written for every new action case
- [ ] Effects handled in handleEffect()
- [ ] Navigation uses AuthEvent / SharedFlow (not state)
- [ ] Repository interface used (not concrete class)
- [ ] Compose previews added for new screens
- [ ] Detekt passes locally: `./gradlew detekt`

## Tests

- Reducer tests: **X passing**
- ViewModel tests: **X passing**
