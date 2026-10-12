/**
 * Admin config editing helpers (Spec 7 TD-11).
 * The form edits a plain JSON document. Secrets are stripped and never sent back.
 * PUT stays a partial merge: only changed branches are included.
 */

export type ConfigDoc = Record<string, unknown>

export interface ConfigChange {
  path: string
  before: unknown
  after: unknown
}

export interface FieldSpec {
  path: string
  label: string
  hint: string
  kind: 'number' | 'boolean' | 'text'
  min?: number
  max?: number
  /** @Positive — zero is rejected. */
  exclusiveMin?: boolean
  step?: number
  /** I8: flying rounds keep the snapshot from start. */
  newRoundsOnly: boolean
}

const SECRET_KEYS = new Set(['apiKey', 'jwtSecret', 'devFixedServerSeed', 'password'])

export const FIELD_SPECS: FieldSpec[] = [
  num('math.growthRate', 'Скорость роста α', 'Множитель K(t) = e^(α·t). Темы на старте подставляют свой α.', { exclusiveMin: true, step: 0.0001 }),
  num('math.houseEdge', 'Доля казино', 'crash-v2: это и шанс краша на 1.00×, и маржа до потолка. 0.08 → отдача около 92% при заборе ниже потолка.', { min: 0, max: 0.5, step: 0.01 }),
  num('math.instantCrashRate', 'Мгновенный взрыв (старые раунды)', 'Новые раунды это поле не используют: мгновенный 1.00× уже входит в долю казино.', { min: 0, max: 1, step: 0.01 }),
  num('math.minCrashPoint', 'Минимальный краш', 'Ниже этого множителя шар не взрывается.', { min: 1, step: 0.01 }),
  num('math.minCashoutMultiplier', 'Минимум для «Забрать»', 'Раньше этого K кнопка не срабатывает.', { min: 1, step: 0.01 }),
  num('scoring.redZoneMultiplier', 'Очки в красной зоне', 'Множитель очков на красных линиях. 1 = как в зелёной.', { min: 0, step: 0.1 }),
  num('greenLevels', 'Зелёные линии', 'Высота спокойной зоны, если тему не выбрали.', { exclusiveMin: true, step: 1 }),
  num('redLevels', 'Красные линии', 'Высота рискованной зоны, если тему не выбрали.', { exclusiveMin: true, step: 1 }),
  num('pointsPerLine', 'Очки за линию', 'Сколько очков даёт каждая новая линия.', { exclusiveMin: true, step: 1 }),
  num('ascentSpeedLinesPerSec', 'Скорость набора линий', 'Сколько линий высоты в секунду.', { exclusiveMin: true, step: 0.1 }),
  num('boosters.spawnProbability', 'Шанс бустера', 'Вероятность, что в раунде появится бустер.', { min: 0, max: 1, step: 0.01 }),
  num('boosters.lineBiasPower', 'Смещение линии бустера', 'Больше 1 — бустер чаще на нижних линиях.', { exclusiveMin: true, step: 0.1 }),
  num('admin.minBetAmount', 'Мин. ставка (запас)', 'Используется, если тема не задала свою.', { exclusiveMin: true, step: 1 }),
  num('admin.maxBetAmount', 'Макс. ставка (запас)', 'Используется, если тема не задала свою.', { exclusiveMin: true, step: 1 }),
  num('admin.minWinAmount', 'Мин. выигрыш', 'Нижняя граница выплаты.', { exclusiveMin: true, step: 1 }),
  num('admin.maxWinMultiplier', 'Потолок множителя (запас)', 'Тема может снизить потолок на старте.', { exclusiveMin: true, step: 1 }),
  num('admin.welcomeBalance', 'Стартовый баланс', 'Начисляется новому игроку только при включённых демо-деньгах.', { min: 0, step: 1, immediate: true }),
  bool('admin.demoMode', 'Демо-деньги', 'Welcome и кнопка пополнения. В бою выключить: иначе игроки печатают баланс.', true),
  bool('admin.acceptingBets', 'Приём ставок', 'Выключите, чтобы остановить новые раунды. Уже летящий шар это не останавливает.', true),
  bool('admin.allowDeposit', 'Разрешить пополнение', 'Работает только вместе с демо-деньгами. Выключается сразу.', true),
  ...themeFields('standard', 'Зелёный шар'),
  ...themeFields('lucky', 'Красный шар'),
]

function num(
  path: string,
  label: string,
  hint: string,
  extra: { min?: number; max?: number; exclusiveMin?: boolean; step?: number; immediate?: boolean },
): FieldSpec {
  return {
    path,
    label,
    hint,
    kind: 'number',
    min: extra.exclusiveMin ? 0 : extra.min,
    max: extra.max,
    exclusiveMin: extra.exclusiveMin,
    step: extra.step,
    newRoundsOnly: !extra.immediate,
  }
}

function bool(path: string, label: string, hint: string, immediate: boolean): FieldSpec {
  return { path, label, hint, kind: 'boolean', newRoundsOnly: !immediate }
}

function themeFields(key: 'standard' | 'lucky', title: string): FieldSpec[] {
  const p = `themes.${key}`
  return [
    num(`${p}.minBetAmount`, `${title}: мин. ставка`, 'Ниже этой суммы раунд этой темы не стартует.', { exclusiveMin: true, step: 1 }),
    num(`${p}.maxBetAmount`, `${title}: макс. ставка`, 'Верхняя граница ставки для темы.', { exclusiveMin: true, step: 1 }),
    num(`${p}.growthRate`, `${title}: α`, 'Скорость роста K именно для этой темы.', { exclusiveMin: true, step: 0.0001 }),
    num(`${p}.maxWinMultiplier`, `${title}: потолок K`, 'Выше этого множителя тема не платит.', { exclusiveMin: true, step: 1 }),
    num(`${p}.greenLevels`, `${title}: зелёные линии`, 'Сколько спокойных линий у темы.', { exclusiveMin: true, step: 1 }),
    num(`${p}.redLevels`, `${title}: красные линии`, 'Сколько рискованных линий у темы.', { exclusiveMin: true, step: 1 }),
  ]
}

export function fieldByPath(path: string): FieldSpec | undefined {
  return FIELD_SPECS.find((f) => f.path === path)
}

export function rangeLabel(spec: FieldSpec): string {
  if (spec.kind !== 'number') return ''
  if (spec.exclusiveMin && spec.max == null) return 'больше 0'
  if (spec.min != null && spec.max != null) return `от ${spec.min} до ${spec.max}`
  if (spec.min != null && !spec.exclusiveMin) return `от ${spec.min}`
  return ''
}

export function withoutSecrets(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutSecrets)
  if (value !== null && typeof value === 'object') {
    const out: ConfigDoc = {}
    for (const [key, child] of Object.entries(value as ConfigDoc)) {
      if (SECRET_KEYS.has(key)) continue
      out[key] = withoutSecrets(child)
    }
    return out
  }
  return value
}

export function cloneConfig(raw: unknown): ConfigDoc {
  const clean = withoutSecrets(raw)
  if (clean === null || typeof clean !== 'object' || Array.isArray(clean)) {
    throw new Error('Конфиг должен быть JSON-объектом')
  }
  return structuredClone(clean) as ConfigDoc
}

export function getPath(doc: unknown, path: string): unknown {
  let cur: unknown = doc
  for (const part of path.split('.')) {
    if (cur === null || typeof cur !== 'object') return undefined
    cur = (cur as ConfigDoc)[part]
  }
  return cur
}

export function setPath(doc: ConfigDoc, path: string, value: unknown): ConfigDoc {
  const root = structuredClone(doc)
  const parts = path.split('.')
  let cur: ConfigDoc | unknown[] = root
  for (let i = 0; i < parts.length - 1; i++) {
    const key = parts[i]
    const next = parts[i + 1]
    const nextIsIndex = /^\d+$/.test(next)
    const existing = (cur as ConfigDoc)[key]
    if (existing === null || typeof existing !== 'object') {
      ;(cur as ConfigDoc)[key] = nextIsIndex ? [] : {}
    }
    cur = (cur as ConfigDoc)[key] as ConfigDoc
  }
  const last = parts[parts.length - 1]
  if (Array.isArray(cur) && /^\d+$/.test(last)) {
    cur[Number(last)] = value
  } else {
    ;(cur as ConfigDoc)[last] = value
  }
  return root
}

function valuesEqual(a: unknown, b: unknown): boolean {
  if (typeof a === 'number' && typeof b === 'number') return Object.is(a, b)
  return Object.is(a, b)
}

export function diffConfig(before: unknown, after: unknown, prefix = ''): ConfigChange[] {
  if (valuesEqual(before, after)) return []
  if (Array.isArray(before) && Array.isArray(after)) {
    const changes: ConfigChange[] = []
    const n = Math.max(before.length, after.length)
    for (let i = 0; i < n; i++) {
      const path = prefix ? `${prefix}.${i}` : String(i)
      changes.push(...diffConfig(before[i], after[i], path))
    }
    return changes
  }
  if (isPlain(before) && isPlain(after)) {
    const keys = new Set([...Object.keys(before), ...Object.keys(after)])
    const changes: ConfigChange[] = []
    for (const key of keys) {
      if (SECRET_KEYS.has(key)) continue
      const path = prefix ? `${prefix}.${key}` : key
      changes.push(...diffConfig(before[key], after[key], path))
    }
    return changes
  }
  if (!prefix) return []
  return [{ path: prefix, before, after }]
}

function isPlain(value: unknown): value is ConfigDoc {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

export function buildPatch(before: ConfigDoc, after: ConfigDoc): ConfigDoc | null {
  const changes = diffConfig(before, after)
  if (changes.length === 0) return null
  const arrayRoots = new Set<string>()
  for (const change of changes) {
    const match = change.path.match(/^(.*)\.\d+(?:\.|$)/)
    if (match) arrayRoots.add(match[1])
  }
  let patch: ConfigDoc = {}
  for (const root of arrayRoots) {
    patch = setPath(patch, root, getPath(after, root))
  }
  for (const change of changes) {
    const covered = [...arrayRoots].some((root) => change.path === root || change.path.startsWith(`${root}.`))
    if (covered) continue
    patch = setPath(patch, change.path, change.after)
  }
  return patch
}

export function formatChange(change: ConfigChange): string {
  return `${change.path}: ${show(change.before)} → ${show(change.after)}`
}

function show(value: unknown): string {
  if (value === undefined) return '—'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

export function validateDraft(doc: ConfigDoc): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const spec of FIELD_SPECS) {
    if (spec.kind === 'boolean') {
      if (typeof getPath(doc, spec.path) !== 'boolean') {
        errors[spec.path] = 'Нужно да или нет'
      }
      continue
    }
    if (spec.kind === 'text') continue
    const value = getPath(doc, spec.path)
    const n = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(n)) {
      errors[spec.path] = 'Нужно число'
      continue
    }
    if (spec.exclusiveMin && !(n > (spec.min ?? 0))) {
      errors[spec.path] = 'Должно быть больше 0'
    } else if (!spec.exclusiveMin && spec.min != null && n < spec.min) {
      errors[spec.path] = `Не меньше ${spec.min}`
    }
    if (spec.max != null && n > spec.max) {
      errors[spec.path] = `Не больше ${spec.max}`
    }
  }
  checkOrder(doc, errors, 'admin.minBetAmount', 'admin.maxBetAmount', 'Мин. ставка больше макс.')
  checkOrder(doc, errors, 'themes.standard.minBetAmount', 'themes.standard.maxBetAmount', 'Мин. ставка темы больше макс.')
  checkOrder(doc, errors, 'themes.lucky.minBetAmount', 'themes.lucky.maxBetAmount', 'Мин. ставка темы больше макс.')
  const cap = numOf(doc, 'admin.maxWinMultiplier')
  if (cap != null) {
    const crash = numOf(doc, 'math.minCrashPoint')
    const cash = numOf(doc, 'math.minCashoutMultiplier')
    if (crash != null && crash > cap) errors['math.minCrashPoint'] = 'Не больше потолка множителя'
    if (cash != null && cash > cap) errors['math.minCashoutMultiplier'] = 'Не больше потолка множителя'
  }
  const tiers = getPath(doc, 'boosters.tiers')
  if (!Array.isArray(tiers) || tiers.length === 0) {
    errors['boosters.tiers'] = 'Нужен хотя бы один тир'
  } else {
    tiers.forEach((tier, index) => {
      const row = isPlain(tier) ? tier : {}
      const name = typeof row.name === 'string' ? row.name.trim() : ''
      if (!name) errors[`boosters.tiers.${index}.name`] = 'Нужно название'
      const mult = Number(row.multiplier)
      const weight = Number(row.probabilityWeight)
      const tierNo = Number(row.tier)
      if (!(mult > 0)) errors[`boosters.tiers.${index}.multiplier`] = 'Множитель больше 0'
      if (!(weight > 0)) errors[`boosters.tiers.${index}.probabilityWeight`] = 'Вес больше 0'
      if (!(tierNo > 0)) errors[`boosters.tiers.${index}.tier`] = 'Номер больше 0'
    })
  }
  return errors
}

function checkOrder(doc: ConfigDoc, errors: Record<string, string>, minPath: string, maxPath: string, message: string) {
  const min = numOf(doc, minPath)
  const max = numOf(doc, maxPath)
  if (min != null && max != null && min > max) errors[minPath] = message
}

function numOf(doc: ConfigDoc, path: string): number | null {
  const n = Number(getPath(doc, path))
  return Number.isFinite(n) ? n : null
}

/** Jakarta paths look like `math.houseEdge must be ...`. Bare names are mapped to form fields. */
export function fieldPathsFromValidationMessage(message: string): string[] {
  const found = new Set<string>()
  for (const part of message.split(';')) {
    const trimmed = part.trim()
    const match = trimmed.match(/^([A-Za-z][\w]*(?:\.[A-Za-z][\w]*|\[\d+\])*)/)
    if (!match) continue
    const token = match[1].replace(/\[(\d+)\]/g, '.$1')
    if (token === 'minBetAmount') {
      found.add('admin.minBetAmount')
      found.add('admin.maxBetAmount')
      continue
    }
    if (token === 'minCrashPoint' || token === 'maxWinMultiplier') {
      found.add('math.minCrashPoint')
      found.add('admin.maxWinMultiplier')
      continue
    }
    if (token === 'minCashoutMultiplier') {
      found.add('math.minCashoutMultiplier')
      found.add('admin.maxWinMultiplier')
      continue
    }
    found.add(token)
  }
  return [...found]
}

export function applyGentleHouseEdge(doc: ConfigDoc): ConfigDoc {
  return setPath(doc, 'math.houseEdge', 0.03)
}

export function applyDepositDisabled(doc: ConfigDoc): ConfigDoc {
  return setPath(doc, 'admin.allowDeposit', false)
}

export function tiersOf(doc: ConfigDoc): ConfigDoc[] {
  const tiers = getPath(doc, 'boosters.tiers')
  if (!Array.isArray(tiers)) return []
  return tiers.filter(isPlain)
}

export function updateTier(doc: ConfigDoc, index: number, key: string, value: unknown): ConfigDoc {
  return setPath(doc, `boosters.tiers.${index}.${key}`, value)
}

export function addTier(doc: ConfigDoc): ConfigDoc {
  const tiers = tiersOf(doc)
  const next = tiers.length + 1
  return setPath(doc, 'boosters.tiers', [
    ...tiers,
    { tier: next, name: `Тир ${next}`, multiplier: 1.5, probabilityWeight: 10 },
  ])
}

export function removeTier(doc: ConfigDoc, index: number): ConfigDoc {
  return setPath(doc, 'boosters.tiers', tiersOf(doc).filter((_, i) => i !== index))
}
