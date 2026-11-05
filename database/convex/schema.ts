import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export const scheduleFields = {
  wordId: v.string(),
  stability: v.number(),
  difficulty: v.number(),
  dueAt: v.number(),
  lastReviewAt: v.number(),
  reviews: v.number(),
  lapses: v.number(),
};

export default defineSchema({
  // Each row is one word's latest schedule. A device asks for everything with a higher seq than it last saw.
  schedule: defineTable({ ...scheduleFields, seq: v.number() }).index("by_word", ["wordId"]).index("by_seq", ["seq"]),
  // Only a hash of each token is kept, so reading this table doesn't let anyone sign in.
  sessions: defineTable({ tokenHash: v.string() }).index("by_token", ["tokenHash"]),
});
