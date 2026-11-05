import { mutation, query } from "./_generated/server";
import type { Doc } from "./_generated/dataModel";
import { v } from "convex/values";
import { scheduleFields } from "./schema";

/**
 * Only the server on the Mac mini calls these, after checking the learner's password or session. Convex functions are
 * public by default, so each one needs the secret that server shares with this deployment.
 */
function requireServer(secret: string) {
  if (secret !== process.env.WORTSCHATZ_SERVER_SECRET) throw new Error("Not allowed");
}

/** The most recent review wins, so devices can sync in any order and sync the same rows twice. */
function isNewer(row: { lastReviewAt: number; reviews: number }, than: Doc<"schedule">) {
  return row.lastReviewAt > than.lastReviewAt || (row.lastReviewAt === than.lastReviewAt && row.reviews > than.reviews);
}

export const sync = mutation({
  args: { secret: v.string(), since: v.number(), rows: v.array(v.object(scheduleFields)) },
  handler: async (ctx, { secret, since, rows }) => {
    requireServer(secret);
    const newest = () => ctx.db.query("schedule").withIndex("by_seq").order("desc").first();
    const seq = ((await newest())?.seq ?? 0) + 1;
    for (const row of rows) {
      const existing = await ctx.db
        .query("schedule")
        .withIndex("by_word", (q) => q.eq("wordId", row.wordId))
        .unique();
      if (!existing) await ctx.db.insert("schedule", { ...row, seq });
      else if (isNewer(row, existing)) await ctx.db.patch(existing._id, { ...row, seq });
    }
    const changes = await ctx.db
      .query("schedule")
      .withIndex("by_seq", (q) => q.gt("seq", since))
      .collect();
    return {
      cursor: (await newest())?.seq ?? 0,
      rows: changes.map(({ wordId, stability, difficulty, dueAt, lastReviewAt, reviews, lapses }) => ({
        wordId, stability, difficulty, dueAt, lastReviewAt, reviews, lapses,
      })),
    };
  },
});

export const addSession = mutation({
  args: { secret: v.string(), tokenHash: v.string(), createdAt: v.number() },
  handler: async (ctx, { secret, tokenHash, createdAt }) => {
    requireServer(secret);
    await ctx.db.insert("sessions", { tokenHash, createdAt });
  },
});

export const hasSession = query({
  args: { secret: v.string(), tokenHash: v.string() },
  handler: async (ctx, { secret, tokenHash }) => {
    requireServer(secret);
    const session = await ctx.db
      .query("sessions")
      .withIndex("by_token", (q) => q.eq("tokenHash", tokenHash))
      .first();
    return session !== null;
  },
});

/** Signs every device out, when the password changes. */
export const clearSessions = mutation({
  args: { secret: v.string() },
  handler: async (ctx, { secret }) => {
    requireServer(secret);
    const sessions = await ctx.db.query("sessions").collect();
    await Promise.all(sessions.map((session) => ctx.db.delete(session._id)));
  },
});
