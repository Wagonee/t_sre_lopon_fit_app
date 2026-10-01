import Chart from 'chart.js/auto';

let charts = [];
export let C = {};
export let ZC = [];
export let PC = [];

const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

export function resetCharts() {
  C = { alt: css('--c-alt'), altFill: css('--c-alt-fill'), hr: css('--c-hr'), spd: css('--c-spd'), pw: css('--c-pw'), band: css('--c-band'), na: css('--c-na') };
  ZC = [1, 2, 3, 4, 5].map((i) => css(`--z${i}`));
  PC = [1, 2, 3, 4, 5, 6, 7].map((i) => css(`--p${i}`));
  Chart.defaults.color = css('--muted');
  Chart.defaults.borderColor = css('--grid');
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  charts.forEach((c) => c.destroy());
  charts = [];
}

export const addChart = (canvas, config) => charts.push(new Chart(canvas, config));

export function lineChart(canvas, x, datasets, yLabel) {
  addChart(canvas, {
    type: 'line',
    data: { datasets },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false, parsing: false, normalized: true,
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      plugins: { legend: { display: false } },
      scales: { x: { type: 'linear', min: 0, max: Math.max(...x) }, y: { title: { display: true, text: yLabel } } },
      elements: { point: { radius: 0 }, line: { borderWidth: 1.5 } },
    },
  });
}

export function barChart(canvas, labels, data, colors, yLabel, tooltip) {
  addChart(canvas, {
    type: 'bar',
    data: { labels, datasets: [{ data, backgroundColor: colors, borderRadius: 4 }] },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: tooltip } } },
      scales: { y: { title: { display: !!yLabel, text: yLabel } }, x: { grid: { display: false } } },
    },
  });
}
