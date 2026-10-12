import { describe, expect, it } from 'vitest'
import { commitPreimage, crashPointV2, sha256Hex, verifyCommitHash } from './provablyFair'

describe('verifyCommitHash', () => {
  it('accepts SHA-256 of serverSeed|clientSeed|nonce', async () => {
    const serverSeedHex = 'ab'.repeat(32)
    const clientSeed = 'player-seed'
    const nonce = 7
    const commit = await sha256Hex(commitPreimage(serverSeedHex, clientSeed, nonce))
    await expect(verifyCommitHash(serverSeedHex, clientSeed, nonce, commit)).resolves.toBe(true)
    await expect(verifyCommitHash(serverSeedHex, clientSeed, nonce, commit.toUpperCase())).resolves.toBe(
      true,
    )
  })

  it('crash-v2 uses house edge as the only edge below the cap', () => {
    expect(crashPointV2(0.5, 0, 100, 1)).toBe(2)
    expect(crashPointV2(0.02, 0.08, 100, 1)).toBe(1)
  })

  it('rejects a tampered commit or nonce', async () => {
    const commit = await sha256Hex(commitPreimage('aa', '', 1))
    await expect(verifyCommitHash('aa', '', 1, '00'.repeat(32))).resolves.toBe(false)
    await expect(verifyCommitHash('aa', '', 2, commit)).resolves.toBe(false)
  })
})
