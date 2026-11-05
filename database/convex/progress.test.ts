import { convexTest } from "convex-test";
import { beforeEach, describe, expect, test } from "vitest";
import { api } from "./_generated/api";
import schema from "./schema";

const modules = import.meta.glob("./**/*.ts");
const secret = "the server's secret";

function row(wordId = "die_zeit", reviewed = 100, reviews = 1) {
  return { wordId, stability: 3, difficulty: 5, dueAt: reviewed + 10, lastReviewAt: reviewed, reviews, lapses: 0 };
}

beforeEach(() => {
  process.env.WORTSCHATZ_SERVER_SECRET = secret;
});

describe("sync", () => {
  const sync = (t: ReturnType<typeof convexTest>, rows: ReturnType<typeof row>[] = [], since = 0) =>
    t.mutation(api.progress.sync, { secret, since, rows });

  test("a review reaches the other device", async () => {
    const t = convexTest(schema, modules);
    expect((await sync(t, [row()])).cursor).toBe(1);
    expect((await sync(t)).rows).toEqual([row()]);
  });

  test("the most recent review wins in either order", async () => {
    const t = convexTest(schema, modules);
    await sync(t, [row("die_zeit", 200, 2)]);
    await sync(t, [row("die_zeit", 100, 1)]);
    expect((await sync(t)).rows).toEqual([row("die_zeit", 200, 2)]);
    await sync(t, [row("die_zeit", 300, 3)]);
    expect((await sync(t)).rows).toEqual([row("die_zeit", 300, 3)]);
  });

  test("the same time falls back to the number of reviews", async () => {
    const t = convexTest(schema, modules);
    await sync(t, [row("die_zeit", 100, 1)]);
    await sync(t, [row("die_zeit", 100, 2)]);
    expect((await sync(t)).rows).toEqual([row("die_zeit", 100, 2)]);
  });

  test("only changes after the cursor come back", async () => {
    const t = convexTest(schema, modules);
    const { cursor } = await sync(t, [row("die_zeit")]);
    expect((await sync(t, [], cursor)).rows).toEqual([]);
    await sync(t, [row("der_tag")]);
    expect((await sync(t, [], cursor)).rows.map((r) => r.wordId)).toEqual(["der_tag"]);
  });

  test("a stale row does not advance the cursor", async () => {
    const t = convexTest(schema, modules);
    const { cursor } = await sync(t, [row("die_zeit", 200, 2)]);
    expect(await sync(t, [row("die_zeit", 100)], cursor)).toEqual({ cursor, rows: [] });
  });

  test("a device keeps its place when rows carry on from an imported cursor", async () => {
    const t = convexTest(schema, modules);
    await t.run((ctx) => ctx.db.insert("schedule", { ...row("die_zeit"), seq: 41 }));
    const { cursor, rows } = await sync(t, [row("der_tag")], 41);
    expect(cursor).toBe(42);
    expect(rows.map((r) => r.wordId)).toEqual(["der_tag"]);
  });
});

describe("sessions", () => {
  test("a session is known until the sessions are cleared", async () => {
    const t = convexTest(schema, modules);
    const has = () => t.query(api.progress.hasSession, { secret, tokenHash: "hash" });
    expect(await has()).toBe(false);
    await t.mutation(api.progress.addSession, { secret, tokenHash: "hash", createdAt: 1 });
    expect(await has()).toBe(true);
    await t.mutation(api.progress.clearSessions, { secret });
    expect(await has()).toBe(false);
  });
});

describe("without the server's secret", () => {
  test.each([
    ["sync", (t: ReturnType<typeof convexTest>, s: string) => t.mutation(api.progress.sync, { secret: s, since: 0, rows: [] })],
    ["hasSession", (t: ReturnType<typeof convexTest>, s: string) => t.query(api.progress.hasSession, { secret: s, tokenHash: "x" })],
    ["addSession", (t: ReturnType<typeof convexTest>, s: string) => t.mutation(api.progress.addSession, { secret: s, tokenHash: "x", createdAt: 1 })],
    ["clearSessions", (t: ReturnType<typeof convexTest>, s: string) => t.mutation(api.progress.clearSessions, { secret: s })],
  ])("%s refuses", async (_, call) => {
    const t = convexTest(schema, modules);
    await expect(call(t, "guess")).rejects.toThrow("Not allowed");
  });

  test("nothing works if the deployment has no secret set", async () => {
    delete process.env.WORTSCHATZ_SERVER_SECRET;
    const t = convexTest(schema, modules);
    await expect(t.query(api.progress.hasSession, { secret: "", tokenHash: "x" })).rejects.toThrow("Not allowed");
  });
});
