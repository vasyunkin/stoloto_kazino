/**
 * Client check of the published commit and crash point.
 * commit = SHA-256( serverSeedHex + "|" + clientSeed + "|" + nonce )
 * precommit = SHA-256(UTF-8 serverSeedHex)
 * crash-v2 uses the house edge, cap and floor returned by /verify.
 */

export function commitPreimage(serverSeedHex: string, clientSeed: string, nonce: number): string {
  return `${serverSeedHex}|${clientSeed}|${nonce}`
}

export async function sha256Hex(text: string): Promise<string> {
  const data = new TextEncoder().encode(text)
  const buf = await crypto.subtle.digest('SHA-256', data)
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export async function verifyCommitHash(
  serverSeedHex: string,
  clientSeed: string,
  nonce: number,
  commitHash: string,
): Promise<boolean> {
  const expected = await sha256Hex(commitPreimage(serverSeedHex, clientSeed, nonce))
  return expected.toLowerCase() === commitHash.trim().toLowerCase()
}

export function extractU(hmac: Uint8Array): number {
  let top7 = 0n
  for (let i = 0; i < 7; i++) top7 = (top7 << 8n) | BigInt(hmac[i] ?? 0)
  const first52 = top7 >> 4n
  const u = Number(first52) / 2 ** 52
  return u === 0 ? Number.MIN_VALUE : u
}

/** crash-v2: RTP at a fixed cashout below the cap is 1 - houseEdge. */
export function crashPointV2(u: number, houseEdge: number, maxWin: number, minCrash: number): number {
  const floor = minCrash
  if (houseEdge >= 1 || u < houseEdge) return round4(floor)
  const v = (u - houseEdge) / (1 - houseEdge)
  const raw = 1 / (1 - Math.min(v, 0.999999999999))
  return round4(Math.min(maxWin, Math.max(floor, raw)))
}

export async function hmacSha256(serverSeedHex: string, message: string): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    'raw',
    hexToBytes(serverSeedHex),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(message))
  return new Uint8Array(sig)
}

export async function recomputeCrashPoint(input: {
  serverSeed: string
  clientSeed: string
  nonce: number
  houseEdge: number
  maxWin: number
  minCrash: number
}): Promise<number> {
  const hmac = await hmacSha256(input.serverSeed, `${input.clientSeed}:${input.nonce}`)
  return crashPointV2(extractU(hmac), input.houseEdge, input.maxWin, input.minCrash)
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000
}

function hexToBytes(hex: string): Uint8Array<ArrayBuffer> {
  const clean = hex.trim().toLowerCase()
  const buffer = new ArrayBuffer(clean.length / 2)
  const out = new Uint8Array(buffer)
  for (let i = 0; i < out.length; i++) out[i] = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16)
  return out
}
