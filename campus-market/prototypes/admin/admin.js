/* ══════════════════════════════════════════════════════════════
   管理端 · 权限隔离（原型内真实生效，非静态示意）
   决策（2026-09-26）：毕设中真实实现权限隔离
     · 前端：按角色渲染菜单 + 禁用无权限按钮（本文件）
     · 后端：同一份 PERMS 矩阵作为接口鉴权依据，写操作二次校验
   落地时把 ROLES / PERMS 换成后端下发的权限点，逻辑不变。
   ══════════════════════════════════════════════════════════════ */

var ROLES = {
  super:    { name: '超级管理员', desc: '全部权限，含封禁与系统设置' },
  reviewer: { name: '审核员',     desc: '仅审核域写权限；不可封禁、不可改配置' },
  operator: { name: '运营',       desc: '商品/用户/订单/内容运营；不可做审核裁决' },
  viewer:   { name: '只读',       desc: '全域只读，用于巡盘与答辩演示' }
};

/* 权限点 → 允许的角色 */
var PERMS = {
  'dashboard.view':  ['super','reviewer','operator','viewer'],
  'item.view':       ['super','reviewer','operator','viewer'],
  'item.offline':    ['super','operator'],
  'item.delete':     ['super'],
  'user.view':       ['super','operator','viewer'],
  'user.verify':     ['super','operator'],
  'user.ban':        ['super'],
  'review.view':     ['super','reviewer','operator','viewer'],
  'review.judge':    ['super','reviewer'],
  'review.forceoff': ['super','reviewer'],
  'review.appeal':   ['super','reviewer'],
  'order.view':      ['super','reviewer','operator','viewer'],
  'order.refund':    ['super','operator'],
  'order.arbitrate': ['super','operator'],
  'content.view':    ['super','operator','viewer'],
  'content.edit':    ['super','operator'],
  'report.view':     ['super','reviewer','operator','viewer'],
  'settings.view':   ['super'],
  'settings.edit':   ['super'],
  'log.view':        ['super','operator']
};

var STORE_KEY = 'cm_admin_role';

function getRole() {
  var r = localStorage.getItem(STORE_KEY);
  return ROLES[r] ? r : 'super';
}
function setRole(r) {
  if (!ROLES[r]) return;
  localStorage.setItem(STORE_KEY, r);
  location.reload();
}
function can(p) {
  var list = PERMS[p];
  return !!list && list.indexOf(getRole()) >= 0;
}

/* ── 应用权限到 DOM ──────────────────────────────────────── */
function applyPerms() {
  var role = getRole();

  // 1) 菜单：无权限直接不渲染（不是置灰，是隔离）
  document.querySelectorAll('.mi[data-perm]').forEach(function (el) {
    if (!can(el.getAttribute('data-perm'))) el.remove();
  });

  // 2) 按钮：无权限 → 禁用 + 锁定标记 + 悬浮说明
  document.querySelectorAll('[data-perm]').forEach(function (el) {
    if (el.classList.contains('mi')) return;
    if (can(el.getAttribute('data-perm'))) return;
    if (el.tagName === 'BUTTON' || el.tagName === 'A') {
      el.setAttribute('disabled', 'disabled');
      el.classList.add('is-locked');
      el.title = '当前角色（' + ROLES[role].name + '）无「' + el.getAttribute('data-perm') + '」权限';
      if (el.tagName === 'A') { el.removeAttribute('href'); el.style.pointerEvents = 'none'; }
    } else {
      el.remove();
    }
  });

  // 3) 角色条：注入到内容区顶部，作为隔离的可视证据
  var slot = document.querySelector('.content');
  if (slot && !document.getElementById('rolebar')) {
    var hidden = [];
    document.querySelectorAll('.is-locked').forEach(function (b) { hidden.push(b.textContent.trim()); });
    var bar = document.createElement('div');
    bar.id = 'rolebar';
    bar.className = 'rolebar';
    bar.innerHTML =
      '<span>当前角色：<b>' + ROLES[role].name + '</b>（' + ROLES[role].desc + '）</span>' +
      '<span class="sep"></span>' +
      '<span>本页已按角色隐藏/锁定 ' + hidden.length + ' 个操作</span>' +
      '<span class="sep"></span>' +
      '<span class="note">前端隐藏仅为体验层，后端对每个写接口做同矩阵校验</span>' +
      '<span style="margin-left:auto;display:flex;align-items:center;gap:6px;">切换角色演示：' +
      '<select class="sel" style="height:24px;min-width:110px;font-size:12px;" id="roleSwitch">' +
      Object.keys(ROLES).map(function (k) {
        return '<option value="' + k + '"' + (k === role ? ' selected' : '') + '>' + ROLES[k].name + '</option>';
      }).join('') +
      '</select></span>';
    slot.insertBefore(bar, slot.firstChild);
    document.getElementById('roleSwitch').addEventListener('change', function (e) { setRole(e.target.value); });
  }

  // 4) 角标：审核队列数量（viewer 也可见，但不可处置）
  document.querySelectorAll('.js-role-name').forEach(function (el) { el.textContent = ROLES[role].name; });
}

/* ── 折叠侧栏 ────────────────────────────────────────────── */
function bindFold() {
  var btn = document.querySelector('.fold');
  var sider = document.querySelector('.sider');
  if (!btn || !sider) return;
  btn.addEventListener('click', function () {
    sider.classList.toggle('collapsed');
    btn.textContent = sider.classList.contains('collapsed') ? '»' : '« 收起菜单';
  });
}

/* ── 抽屉 / 模态开关（纯演示，不含业务逻辑） ─────────────── */
function bindOverlays() {
  document.querySelectorAll('[data-open]').forEach(function (el) {
    el.addEventListener('click', function () {
      var t = document.getElementById(el.getAttribute('data-open'));
      if (t) t.style.display = t.classList.contains('modal-mask') ? 'flex' : 'block';
    });
  });
  document.querySelectorAll('[data-close]').forEach(function (el) {
    el.addEventListener('click', function () {
      var box = el.closest('.drawer-mask, .modal-mask, .drawer, .modal');
      var mask = box && box.closest('.drawer-mask, .modal-mask');
      (mask || box).style.display = 'none';
    });
  });
  document.querySelectorAll('.drawer-mask').forEach(function (m) {
    m.addEventListener('click', function (e) { if (e.target === m) m.style.display = 'none'; });
  });
}

document.addEventListener('DOMContentLoaded', function () {
  applyPerms();
  bindFold();
  bindOverlays();
});

/* ── 登录页：选账号 = 选角色，写入后进入看板 ─────────────── */
function loginAs(role) {
  setRole(role);
  location.href = 'dashboard.html';
}
