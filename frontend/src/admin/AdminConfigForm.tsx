import type { ReactNode } from 'react'
import {
  FIELD_SPECS,
  addTier,
  rangeLabel,
  removeTier,
  tiersOf,
  updateTier,
  type ConfigDoc,
  type FieldSpec,
} from './configModel'

interface AdminConfigFormProps {
  draft: ConfigDoc
  errors: Record<string, string>
  onChange: (path: string, value: unknown) => void
  onReplace: (next: ConfigDoc) => void
}

export function AdminConfigForm({ draft, errors, onChange, onReplace }: AdminConfigFormProps) {
  const math = FIELD_SPECS.filter((f) => f.path.startsWith('math.'))
  const flight = FIELD_SPECS.filter((f) =>
    ['greenLevels', 'redLevels', 'pointsPerLine', 'ascentSpeedLinesPerSec'].includes(f.path) ||
    f.path.startsWith('scoring.'),
  )
  const boosterScalars = FIELD_SPECS.filter((f) => f.path.startsWith('boosters.') && !f.path.includes('tiers'))
  const wallet = FIELD_SPECS.filter((f) => f.path.startsWith('admin.'))
  const standard = FIELD_SPECS.filter((f) => f.path.startsWith('themes.standard.'))
  const lucky = FIELD_SPECS.filter((f) => f.path.startsWith('themes.lucky.'))

  return (
    <div className="admin-form">
      <Section title="Математика" open>
        <p className="admin-muted">Меняет взрыв и скорость роста только у новых раундов.</p>
        <div className="admin-grid">
          {math.map((spec) => (
            <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
          ))}
        </div>
      </Section>

      <Section title="Темы шаров" open>
        <div className="admin-theme-grid">
          <article className="admin-theme">
            <h3>Зелёный (STANDARD)</h3>
            <div className="admin-grid">
              {standard.map((spec) => (
                <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
              ))}
            </div>
          </article>
          <article className="admin-theme">
            <h3>Красный (LUCKY)</h3>
            <div className="admin-grid">
              {lucky.map((spec) => (
                <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
              ))}
            </div>
          </article>
        </div>
      </Section>

      <Section title="Бустеры">
        <div className="admin-grid">
          {boosterScalars.map((spec) => (
            <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
          ))}
        </div>
        {errors['boosters.tiers'] && <p className="admin-field-error">{errors['boosters.tiers']}</p>}
        <table className="admin-table">
          <thead>
            <tr>
              <th>Тир</th>
              <th>Название</th>
              <th>Множитель K</th>
              <th>Вес</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {tiersOf(draft).map((tier, index) => (
              <tr key={index}>
                <td>
                  <TierInput
                    path={`boosters.tiers.${index}.tier`}
                    type="number"
                    value={tier.tier}
                    error={errors[`boosters.tiers.${index}.tier`]}
                    onChange={(value) => onReplace(updateTier(draft, index, 'tier', value))}
                  />
                </td>
                <td>
                  <TierInput
                    path={`boosters.tiers.${index}.name`}
                    type="text"
                    value={tier.name}
                    error={errors[`boosters.tiers.${index}.name`]}
                    onChange={(value) => onReplace(updateTier(draft, index, 'name', value))}
                  />
                </td>
                <td>
                  <TierInput
                    path={`boosters.tiers.${index}.multiplier`}
                    type="number"
                    value={tier.multiplier}
                    error={errors[`boosters.tiers.${index}.multiplier`]}
                    onChange={(value) => onReplace(updateTier(draft, index, 'multiplier', value))}
                  />
                </td>
                <td>
                  <TierInput
                    path={`boosters.tiers.${index}.probabilityWeight`}
                    type="number"
                    value={tier.probabilityWeight}
                    error={errors[`boosters.tiers.${index}.probabilityWeight`]}
                    onChange={(value) => onReplace(updateTier(draft, index, 'probabilityWeight', value))}
                  />
                </td>
                <td>
                  <button className="admin-btn secondary" type="button" onClick={() => onReplace(removeTier(draft, index))}>
                    Убрать
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <button className="admin-btn secondary" type="button" onClick={() => onReplace(addTier(draft))}>
          Добавить тир
        </button>
      </Section>

      <Section title="Полёт и очки">
        <div className="admin-grid">
          {flight.map((spec) => (
            <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
          ))}
        </div>
      </Section>

      <Section title="Кошелёк" open>
        <div className="admin-grid">
          {wallet.map((spec) => (
            <Field key={spec.path} spec={spec} draft={draft} error={errors[spec.path]} onChange={onChange} />
          ))}
        </div>
      </Section>
    </div>
  )
}

function Section({ title, open, children }: { title: string; open?: boolean; children: ReactNode }) {
  return (
    <details className="admin-section" open={open}>
      <summary>{title}</summary>
      <div className="admin-section-body">{children}</div>
    </details>
  )
}

function Field({
  spec,
  draft,
  error,
  onChange,
}: {
  spec: FieldSpec
  draft: ConfigDoc
  error?: string
  onChange: (path: string, value: unknown) => void
}) {
  const id = fieldId(spec.path)
  const raw = read(draft, spec.path)
  return (
    <div className={`admin-field${error ? ' is-invalid' : ''}`}>
      <label className="admin-label" htmlFor={id} title={spec.hint}>
        {spec.label}
        <span className={`admin-badge${spec.newRoundsOnly ? '' : ' is-now'}`}>
          {spec.newRoundsOnly ? 'только новые раунды' : 'сразу'}
        </span>
      </label>
      <p className="admin-hint">{spec.hint}</p>
      {spec.kind === 'boolean' ? (
        <label className="admin-check">
          <input
            id={id}
            data-field={spec.path}
            type="checkbox"
            checked={raw === true}
            aria-invalid={error ? true : undefined}
            onChange={(e) => onChange(spec.path, e.target.checked)}
          />
          {raw === true ? 'включено' : 'выключено'}
        </label>
      ) : (
        <input
          id={id}
          data-field={spec.path}
          className="admin-input"
          type="number"
          step={spec.step ?? 'any'}
          min={spec.exclusiveMin ? undefined : spec.min}
          max={spec.max}
          value={raw === undefined || raw === null ? '' : String(raw)}
          aria-invalid={error ? true : undefined}
          onChange={(e) => {
            const text = e.target.value
            onChange(spec.path, text === '' ? '' : Number(text))
          }}
        />
      )}
      {spec.kind === 'number' && rangeLabel(spec) && <p className="admin-range">Допустимо: {rangeLabel(spec)}</p>}
      {error && <p className="admin-field-error">{error}</p>}
    </div>
  )
}

function TierInput({
  path,
  type,
  value,
  error,
  onChange,
}: {
  path: string
  type: 'number' | 'text'
  value: unknown
  error?: string
  onChange: (value: unknown) => void
}) {
  return (
    <>
      <input
        className={`admin-input${error ? ' is-invalid' : ''}`}
        data-field={path}
        type={type}
        step={type === 'number' ? 'any' : undefined}
        value={value === undefined || value === null ? '' : String(value)}
        aria-invalid={error ? true : undefined}
        onChange={(e) => {
          if (type === 'text') onChange(e.target.value)
          else onChange(e.target.value === '' ? '' : Number(e.target.value))
        }}
      />
      {error && <p className="admin-field-error">{error}</p>}
    </>
  )
}

function read(doc: ConfigDoc, path: string): unknown {
  let cur: unknown = doc
  for (const part of path.split('.')) {
    if (cur === null || typeof cur !== 'object') return undefined
    cur = (cur as ConfigDoc)[part]
  }
  return cur
}

function fieldId(path: string): string {
  return `config-${path.replace(/[^A-Za-z0-9]+/g, '-')}`
}
