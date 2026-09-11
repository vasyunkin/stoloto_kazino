package com.stoloto.balloongame.domain.entity;

/**
 * Direction / purpose of a wallet ledger entry.
 * Stored as VARCHAR(20) in wallet_ledger.type.
 */
public enum LedgerType {
    /** Bet amount debited from player balance at round start. */
    DEBIT_BET,
    /** Win amount credited at cashout. */
    CREDIT_WIN,
    /** Bet amount refunded on VOID (server restart recovery). */
    CREDIT_REFUND,
    /** Manual deposit (dev/demo only — guarded by game.admin.allow-deposit). */
    CREDIT_DEPOSIT
}
