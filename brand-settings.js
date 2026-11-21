/* Shared Anamika Lux brand settings and website admin editor. */
(function () {
  "use strict";
  const SUPABASE_URL = "https://uwbcgwvrvtvggmaejxfu.supabase.co";
  const SUPABASE_KEY = "sb_publishable_CqhbUiMSpMpBSl2X_vKdHg_WimgwESl";
  const db = window.ANAMIKA_DB || (window.supabase && window.supabase.createClient(SUPABASE_URL, SUPABASE_KEY));
  if (!db) return;
  window.ANAMIKA_DB = db;

  const DEFAULTS = {
    id: true,
    brand_color: "#D4AF37",
    logo_url: "/the-anamika-lux-logo.png",
  };

  function rgb(hex) {
    const value = String(hex || DEFAULTS.brand_color).replace("#", "");
    return [0, 2, 4].map(function (i) { return parseInt(value.slice(i, i + 2), 16) || 0; }).join(", ");
  }

  function applyBrandSettings(settings) {
    const value = Object.assign({}, DEFAULTS, settings || {});
    const color = /^#[0-9a-f]{6}$/i.test(value.brand_color) ? value.brand_color : DEFAULTS.brand_color;
    const styleId = "anamika-runtime-brand-theme";
    let style = document.getElementById(styleId);
    if (!style) {
      style = document.createElement("style");
      style.id = styleId;
      document.head.appendChild(style);
    }
    style.textContent = `
      :root { --anamika-brand: ${color}; --anamika-brand-rgb: ${rgb(color)}; }
      .text-gold-200,.text-gold-300,.text-gold-400,.text-gold-500,
      [class*="text-[#D4AF37]"],[class*="text-[#E7CE75]"] { color: var(--anamika-brand) !important; }
      .bg-gold-500,.bg-gold-400,[class*="bg-[#D4AF37]"] { background-color: var(--anamika-brand) !important; }
      .border-gold-500,.border-gold-400,[class*="border-[#D4AF37]"] { border-color: var(--anamika-brand) !important; }
      .btn-gold-solid,.btn-main { background: linear-gradient(135deg, var(--anamika-brand), color-mix(in srgb, var(--anamika-brand) 68%, #000)) !important; }
      .btn-gold-outline { border-color: rgba(var(--anamika-brand-rgb),.55) !important; color: var(--anamika-brand) !important; }
      .glass-card-featured { border-color: rgba(var(--anamika-brand-rgb),.7) !important; }
      .field:focus { border-color: var(--anamika-brand) !important; }
      .bottom-nav button.active { color: var(--anamika-brand) !important; }
    `;

    if (value.logo_url) {
      document.querySelectorAll('img[src*="the-anamika-lux-logo.png"], img[data-anamika-logo]').forEach(function (img) {
        img.src = value.logo_url;
        img.setAttribute("data-anamika-logo", "true");
      });
      const icon = document.querySelector('link[rel="icon"]');
      if (icon) icon.href = value.logo_url;
    }
    window.ANAMIKA_BRAND_SETTINGS = value;
    document.dispatchEvent(new CustomEvent("anamika:brand-updated", { detail: value }));
  }

  async function getSettings() {
    const result = await db.from("app_settings").select("*").eq("id", true).maybeSingle();
    if (result.error) throw result.error;
    return Object.assign({}, DEFAULTS, result.data || {});
  }

  async function refreshPublicSettings() {
    try { applyBrandSettings(await getSettings()); }
    catch (error) { console.warn("Anamika brand settings unavailable:", error); }
  }

  refreshPublicSettings();
  const publicChannel = db.channel("anamika-brand-settings-live")
    .on("postgres_changes", { event: "*", schema: "public", table: "app_settings" }, refreshPublicSettings)
    .subscribe();

  function adminToast(message) {
    if (typeof window.showToast === "function") window.showToast(message);
    else if (typeof window.toast === "function") window.toast(message);
    else window.alert(message);
  }

  function addAdminBrandPanel() {
    const settingsTab = document.getElementById("adminTab-settings");
    if (!settingsTab || document.getElementById("anamikaBrandPanel")) return;
    const panel = document.createElement("section");
    panel.id = "anamikaBrandPanel";
    panel.className = "glass-card p-5 rounded-2xl border border-gold-500/30 space-y-4";
    panel.innerHTML = `
      <div><h3 class="text-sm font-bold text-gold-300 uppercase tracking-wider">Brand Settings</h3>
      <p class="text-xs text-neutral-400 mt-1">Changes are saved online and sync to the website.</p></div>
      <div class="flex items-center gap-4">
        <img id="brandLogoPreview" src="/the-anamika-lux-logo.png" alt="Brand logo preview" class="w-16 h-16 rounded-xl object-contain bg-black border border-white/10">
        <div class="flex-1 min-w-0"><label class="field-label">Upload new logo</label>
        <input id="brandLogoFile" type="file" accept="image/png,image/jpeg,image/webp,image/svg+xml" class="w-full text-xs text-neutral-300"></div>
      </div>
      <div><label class="block text-[10px] text-neutral-400 uppercase font-semibold mb-1">Primary brand colour</label><div class="flex items-center gap-3">
        <input id="brandColorInput" type="color" value="#D4AF37" class="w-12 h-11 rounded-lg border-0 bg-transparent">
        <span id="brandColorValue" class="text-xs font-mono text-neutral-300">#D4AF37</span></div></div>
      <button id="saveBrandSettingsBtn" type="button" class="btn-gold-solid w-full rounded-xl py-3 font-extrabold">Save & publish settings</button>
      <p class="text-[10px] text-neutral-500">APK updates download a new installer. Android may ask the user to confirm installation.</p>`;
    settingsTab.appendChild(panel);

    const color = panel.querySelector("#brandColorInput");
    color.addEventListener("input", function () {
      panel.querySelector("#brandColorValue").textContent = color.value.toUpperCase();
      applyBrandSettings(Object.assign({}, window.ANAMIKA_BRAND_SETTINGS || DEFAULTS, { brand_color: color.value }));
    });
    panel.querySelector("#brandLogoFile").addEventListener("change", function () {
      const file = this.files && this.files[0];
      if (file) panel.querySelector("#brandLogoPreview").src = URL.createObjectURL(file);
    });
    panel.querySelector("#saveBrandSettingsBtn").addEventListener("click", saveAdminBrandSettings);
  }

  async function loadAdminBrandSettings() {
    addAdminBrandPanel();
    const panel = document.getElementById("anamikaBrandPanel");
    if (!panel) return;
    try {
      const settings = await getSettings();
      applyBrandSettings(settings);
      panel.querySelector("#brandColorInput").value = settings.brand_color;
      panel.querySelector("#brandColorValue").textContent = settings.brand_color.toUpperCase();
      panel.querySelector("#brandLogoPreview").src = settings.logo_url;
    } catch (error) { adminToast("Could not load brand settings: " + (error.message || "Please retry.")); }
  }

  async function saveAdminBrandSettings() {
    const panel = document.getElementById("anamikaBrandPanel");
    const button = panel && panel.querySelector("#saveBrandSettingsBtn");
    if (!panel || !button) return;
    const color = panel.querySelector("#brandColorInput").value;
    const file = panel.querySelector("#brandLogoFile").files[0];
    if (!/^#[0-9a-f]{6}$/i.test(color)) { adminToast("Choose a valid brand colour."); return; }
    button.disabled = true; button.textContent = "Publishing...";
    try {
      let logoUrl = (window.ANAMIKA_BRAND_SETTINGS || DEFAULTS).logo_url;
      if (file) {
        if (file.size > 5 * 1024 * 1024) throw new Error("Logo must be 5 MB or smaller.");
        if (!/^image\/(png|jpeg|webp|svg\+xml)$/.test(file.type)) throw new Error("Use PNG, JPG, WEBP or SVG for the logo.");
        const ext = file.type === "image/svg+xml" ? "svg" : (file.type === "image/jpeg" ? "jpg" : file.type.split("/")[1]);
        const path = "brand-logo-" + Date.now() + "." + ext;
        const upload = await db.storage.from("brand-assets").upload(path, file, { upsert: true, contentType: file.type });
        if (upload.error) throw upload.error;
        logoUrl = db.storage.from("brand-assets").getPublicUrl(path).data.publicUrl;
      }
      const settings = {
        id: true, brand_color: color, logo_url: logoUrl,
        updated_at: new Date().toISOString()
      };
      const result = await db.from("app_settings").upsert(settings, { onConflict: "id" });
      if (result.error) throw result.error;
      applyBrandSettings(settings);
      panel.querySelector("#brandLogoPreview").src = logoUrl;
      panel.querySelector("#brandLogoFile").value = "";
      adminToast("Website brand settings published.");
    } catch (error) {
      console.error("Brand settings save failed:", error);
      adminToast("Save failed: " + (error.message || "Please retry."));
    } finally {
      button.disabled = false; button.textContent = "Save & publish settings";
    }
  }

  if (document.getElementById("adminTab-settings")) {
    addAdminBrandPanel();
    const originalOpen = window.openAdminPortal;
    if (typeof originalOpen === "function") {
      window.openAdminPortal = async function () {
        const result = await originalOpen.apply(this, arguments);
        await loadAdminBrandSettings();
        return result;
      };
    }
  }
})();