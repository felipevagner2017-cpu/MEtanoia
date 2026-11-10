import fs from "node:fs";
import vm from "node:vm";

const source = fs.readFileSync("web/app.js", "utf8");
const html = fs.readFileSync("web/index.html", "utf8");
if (!html.includes('data-agenda-view="day"') || !html.includes('data-agenda-view="week"') || !html.includes('data-agenda-view="month"') || !source.includes('agendaViewMode="month"')) {
  throw new Error("Day, week and month agenda views are not wired into the PWA.");
}
for (const field of ['name="description"', 'name="location"', 'name="endTime"', 'name="reminders"']) {
  if (!source.includes(field)) throw new Error("Agenda form is missing " + field + ".");
}
if (!source.includes("Array.isArray(item.reminders)?item.reminders:[120,60,30]")) {
  throw new Error("Per-event reminder preferences are not used by the reminder scheduler.");
}
const fixedLine = source.match(/const fixed=x=>[^,;\n]+/)?.[0];
const occurrenceLine = source.match(/const occurrence=\(x,k\)=>[^;]+;/)?.[0];
const occursLine = source.match(/function occursOn\(x,d\)\{[^}]+\}/)?.[0];
if (!fixedLine || !occurrenceLine || !occursLine) {
  throw new Error("Could not extract the live recurrence functions from web/app.js.");
}
const key = (date) => [
  date.getFullYear(),
  String(date.getMonth() + 1).padStart(2, "0"),
  String(date.getDate()).padStart(2, "0"),
].join("-");
const context = { key };
vm.runInNewContext(
  fixedLine + "; " + occurrenceLine + "; " + occursLine + "; globalThis.api={fixed,occurrence,occursOn};",
  context
);
const { fixed, occurrence, occursOn } = context.api;
const date = (day) => new Date(2026, 10, day);

const daily = { schedule: "daily", date: "2026-11-08", endDate: "2026-11-11" };
if (!fixed(daily) || !occursOn(daily, date(9)) || !occursOn(daily, date(11)) || occursOn(daily, date(12))) {
  throw new Error("Daily recurrence boundaries are incorrect.");
}
const weekly = { schedule: "weekly", date: "2026-11-01", weekdays: [1, 3, 5] };
if (!fixed(weekly) || !occursOn(weekly, date(9)) || occursOn(weekly, date(10)) || !occursOn(weekly, date(11))) {
  throw new Error("Weekly recurrence weekdays are incorrect.");
}
const once = { schedule: "once", date: "2026-11-10" };
if (fixed(once) || !occursOn(once, date(10)) || occursOn(once, date(11))) {
  throw new Error("One-time occurrence behavior is incorrect.");
}
const legacy = { fixedWeekly: true, date: "2026-11-01", weekdays: [1] };
if (!fixed(legacy) || !occursOn(legacy, date(9)) || occursOn(legacy, date(10))) {
  throw new Error("Legacy weekly recurrence compatibility is broken.");
}
const completed = { completions: { "2026-11-10": { done: true, note: "ok" } } };
if (!occurrence(completed, "2026-11-10").done || occurrence(completed, "2026-11-09").done) {
  throw new Error("Per-occurrence completion history is not preserved.");
}
console.log("PWA agenda recurrence tests passed: daily, weekly, one-time, legacy and per-date completion history.");
