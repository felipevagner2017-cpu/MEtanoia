/* Local PWA vault: AES-GCM records in IndexedDB, with a PBKDF2-wrapped DEK. */
(() => {
  "use strict";
  const DB_NAME = "metonoia-local-vault-v1";
  const STORE = "vault";
  const USER_ID = "metonoia-local-pwa-v1";
  const RECORD_ID = "local-app-state";
  const DIMENSION = "LOCAL";
  const RECORD_TYPE = "pwa-app-state";
  const EXPORT_USER_ID = "metonoia-export-v1";
  const EXPORT_RECORD_ID = "full-backup";
  const EXPORT_DIMENSION = "EXPORT";
  const EXPORT_TYPE = "application-state";
  const encoder = new TextEncoder();

  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE);
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Não foi possível abrir o armazenamento protegido."));
      request.onblocked = () => reject(new Error("O armazenamento protegido está ocupado por outra aba. Feche as outras abas do MEtanoia e tente novamente."));
    });
  }

  function read(db, key) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, "readonly");
      const request = tx.objectStore(STORE).get(key);
      request.onsuccess = () => resolve(request.result ?? null);
      request.onerror = () => reject(request.error || new Error("Falha ao ler o armazenamento protegido."));
      tx.onabort = () => reject(tx.error || new Error("Leitura cancelada."));
    });
  }

  function write(db, entries, removals = []) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      const store = tx.objectStore(STORE);
      for (const [key, value] of Object.entries(entries)) store.put(value, key);
      for (const key of removals) store.delete(key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error("Falha ao gravar os dados protegidos."));
      tx.onabort = () => reject(tx.error || new Error("Gravação protegida cancelada."));
    });
  }

  function canonical(value) {
    if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]";
    if (value && typeof value === "object") {
      return "{" + Object.keys(value).sort().map(key => JSON.stringify(key) + ":" + canonical(value[key])).join(",") + "}";
    }
    return JSON.stringify(value);
  }

  function validateData(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("A estrutura do arquivo de dados não é válida.");
    for (const key of ["items", "journal", "reviews", "words"]) {
      if (value[key] !== undefined && !Array.isArray(value[key])) throw new Error("O arquivo contém uma coleção inválida: " + key);
    }
    return {
      items: Array.isArray(value.items) ? value.items : [],
      journal: Array.isArray(value.journal) ? value.journal : [],
      reviews: Array.isArray(value.reviews) ? value.reviews : [],
      words: Array.isArray(value.words) ? value.words : []
    };
  }

  function passphraseGate(mode, errorMessage = "") {
    return new Promise(resolve => {
      const create = mode === "create" || mode === "export";
      const title = mode === "create" ? "Proteção dos dados locais" :
        mode === "export" ? "Proteger cópia de segurança" :
        mode === "restore" ? "Desbloquear cópia de segurança" : "Desbloquear MEtanoia";
      const description = mode === "create"
        ? "Seus registros serão cifrados neste aparelho. Crie uma frase exclusiva com pelo menos 12 caracteres. Ela não será enviada ao servidor. Se perdê-la sem uma cópia protegida, não será possível recuperar os dados locais."
        : mode === "export"
        ? "A cópia será protegida com AES-GCM. Guarde esta frase separadamente: sem ela, o arquivo não poderá ser recuperado."
        : mode === "restore"
        ? "Informe a frase usada ao criar esta cópia protegida."
        : "Seus registros locais estão protegidos. Informe sua frase para abrir a agenda e o diário.";
      const overlay = document.createElement("div");
      overlay.className = "modalbg";
      overlay.style.zIndex = "9999";
      overlay.innerHTML =
        '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="vaultTitle">' +
          '<div class="eyebrow">MEtonoia · Segurança local</div>' +
          '<h2 id="vaultTitle">' + title + '</h2>' +
          '<p class="muted">' + description + '</p>' +
          '<p class="reading-note" id="vaultError" role="alert"></p>' +
          '<form id="vaultPassphraseForm">' +
            '<label for="vaultPassphrase">' + (mode === "restore" ? "Frase da cópia" : "Frase de criptografia") + '</label>' +
            '<input id="vaultPassphrase" class="field" type="password" minlength="12" maxlength="256" autocomplete="off" required>' +
            (create ? '<label for="vaultPassphraseConfirm">Confirme a frase</label><input id="vaultPassphraseConfirm" class="field" type="password" minlength="12" maxlength="256" autocomplete="off" required>' : '') +
            '<div class="row"><button class="btn gold" type="submit">' + (mode === "create" ? "Ativar proteção" : mode === "export" ? "Gerar cópia protegida" : "Desbloquear") + '</button><button class="btn" type="button" id="vaultCancel">Cancelar</button></div>' +
          '</form>' +
        '</div>';
      document.body.appendChild(overlay);
      if (errorMessage) overlay.querySelector("#vaultError").textContent = errorMessage;
      const form = overlay.querySelector("#vaultPassphraseForm");
      const input = overlay.querySelector("#vaultPassphrase");
      input.focus();
      form.addEventListener("submit", event => {
        event.preventDefault();
        const phrase = input.value;
        if (phrase.length < 12) { input.setCustomValidity("Use pelo menos 12 caracteres."); input.reportValidity(); return; }
        if (create && phrase !== overlay.querySelector("#vaultPassphraseConfirm").value) {
          overlay.querySelector("#vaultPassphraseConfirm").setCustomValidity("As frases não coincidem.");
          overlay.querySelector("#vaultPassphraseConfirm").reportValidity();
          return;
        }
        overlay.remove();
        resolve(phrase);
      });
      form.querySelectorAll("input").forEach(field => field.addEventListener("input", () => field.setCustomValidity("")));
      overlay.querySelector("#vaultCancel").addEventListener("click", () => { overlay.remove(); resolve(null); });
    });
  }

  async function createLocalKey() {
    const cryptoApi = window.MEtanoiaAccountCrypto;
    while (true) {
      const phrase = await passphraseGate("create");
      if (phrase === null) throw new Error("A proteção local não foi inicializada. Nenhum dado foi alterado.");
      try {
        const envelope = await cryptoApi.createEnvelope(USER_ID, phrase);
        const key = await cryptoApi.openEnvelope(USER_ID, phrase, envelope);
        return { envelope, key };
      } catch (error) {
        const retry = await passphraseGate("create", error.message || "Não foi possível criar a chave local.");
        if (retry === null) throw new Error("A proteção local não foi inicializada.");
        const nextEnvelope = await cryptoApi.createEnvelope(USER_ID, retry);
        const nextKey = await cryptoApi.openEnvelope(USER_ID, retry, nextEnvelope);
        return { envelope: nextEnvelope, key: nextKey };
      }
    }
  }

  async function unlockLocalKey(envelope) {
    const cryptoApi = window.MEtanoiaAccountCrypto;
    while (true) {
      const phrase = await passphraseGate("unlock");
      if (phrase === null) throw new Error("O armazenamento permanece bloqueado; os dados não foram alterados.");
      try {
        return await cryptoApi.openEnvelope(USER_ID, phrase, envelope);
      } catch {
        const retry = await passphraseGate("unlock", "Frase incorreta ou envelope inválido. Tente novamente.");
        if (retry === null) throw new Error("O armazenamento permanece bloqueado.");
        try { return await cryptoApi.openEnvelope(USER_ID, retry, envelope); }
        catch { /* retry with the standard gate */ }
      }
    }
  }

  async function decryptLocalPayload(key, ciphertext) {
    const cryptoApi = window.MEtanoiaAccountCrypto;
    const bytes = await cryptoApi.decryptRecord(USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, ciphertext);
    return validateData(JSON.parse(new TextDecoder().decode(bytes)));
  }

  async function open(legacyKey, defaultData) {
    if (!window.crypto?.subtle || !window.indexedDB || !window.MEtanoiaAccountCrypto) {
      throw new Error("Este navegador não oferece os recursos necessários para proteger os dados locais. O armazenamento antigo foi preservado.");
    }
    const db = await openDb();
    const legacyRaw = localStorage.getItem(legacyKey);
    let envelope = await read(db, "key-envelope");
    let ciphertext = await read(db, "app-state");
    let key;
    let data;

    if (!envelope) {
      if (ciphertext) throw new Error("O armazenamento protegido está incompleto. A cópia antiga não foi alterada.");
      const legacyData = legacyRaw === null ? validateData(defaultData) : validateData(JSON.parse(legacyRaw));
      const created = await createLocalKey();
      key = created.key;
      envelope = created.envelope;
      ciphertext = await window.MEtanoiaAccountCrypto.encryptRecord(
        USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, JSON.stringify(legacyData)
      );
      await write(db, { "key-envelope": envelope, "app-state": ciphertext });
      const verified = await decryptLocalPayload(key, await read(db, "app-state"));
      if (canonical(verified) !== canonical(legacyData)) throw new Error("A validação da migração local falhou. A cópia antiga foi preservada.");
      data = verified;
      if (legacyRaw !== null) localStorage.removeItem(legacyKey);
    } else {
      key = await unlockLocalKey(envelope);
      if (ciphertext) {
        try {
          data = await decryptLocalPayload(key, ciphertext);
        } catch (error) {
          const previous = await read(db, "previous-app-state");
          if (previous) {
            data = await decryptLocalPayload(key, previous);
            await write(db, { "app-state": previous }, ["previous-app-state"]);
          } else if (legacyRaw !== null) {
            // The legacy copy is retained until a verified encrypted write commits.
            data = validateData(JSON.parse(legacyRaw));
            const recoveredCiphertext = await window.MEtanoiaAccountCrypto.encryptRecord(
              USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, JSON.stringify(data)
            );
            await write(db, { "app-state": recoveredCiphertext });
            const verified = await decryptLocalPayload(key, await read(db, "app-state"));
            if (canonical(verified) !== canonical(data)) throw new Error("A recuperação da cópia antiga não passou na autenticação.");
            data = verified;
          } else {
            throw new Error("Não foi possível autenticar os dados locais. Nenhum dado foi sobrescrito.");
          }
        }
      } else if (legacyRaw !== null) {
        data = validateData(JSON.parse(legacyRaw));
        ciphertext = await window.MEtanoiaAccountCrypto.encryptRecord(
          USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, JSON.stringify(data)
        );
        await write(db, { "app-state": ciphertext });
        const verified = await decryptLocalPayload(key, await read(db, "app-state"));
        if (canonical(verified) !== canonical(data)) throw new Error("A validação da migração local falhou. A cópia antiga foi preservada.");
        data = verified;
      } else {
        throw new Error("O envelope de criptografia existe, mas os dados cifrados estão ausentes. Nenhum registro foi recriado ou sobrescrito.");
      }

      if (legacyRaw !== null) {
        const legacyData = validateData(JSON.parse(legacyRaw));
        if (canonical(legacyData) !== canonical(data)) {
          throw new Error("Há duas versões diferentes dos registros locais. Mantive ambas e interrompi a abertura para evitar perda de dados.");
        }
        localStorage.removeItem(legacyKey);
      }
    }

    let queue = Promise.resolve();
    const save = nextData => {
      const validated = validateData(nextData);
      queue = queue.catch(() => {}).then(async () => {
        const encrypted = await window.MEtanoiaAccountCrypto.encryptRecord(
          USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, JSON.stringify(validated)
        );
        await write(db, { "app-state": encrypted });
      });
      return queue;
    };

    async function exportBackup(passphrase, currentData) {
      const exportEnvelope = await window.MEtanoiaAccountCrypto.createEnvelope(EXPORT_USER_ID, passphrase);
      const exportKey = await window.MEtanoiaAccountCrypto.openEnvelope(EXPORT_USER_ID, passphrase, exportEnvelope);
      try {
        const exportCiphertext = await window.MEtanoiaAccountCrypto.encryptRecord(
          EXPORT_USER_ID, EXPORT_RECORD_ID, EXPORT_DIMENSION, EXPORT_TYPE, exportKey, JSON.stringify(validateData(currentData))
        );
        return JSON.stringify({
          format: "metonoia-encrypted-backup",
          version: 1,
          createdAt: new Date().toISOString(),
          kdf: "PBKDF2-SHA256-600000",
          cipher: "AES-256-GCM",
          envelope: exportEnvelope,
          payload: exportCiphertext
        });
      } finally {
        exportKey.fill(0);
      }
    }

    async function restoreBackup(fileText, passphrase) {
      const backup = JSON.parse(fileText);
      if (!backup || backup.format !== "metonoia-encrypted-backup" || backup.version !== 1 ||
          backup.kdf !== "PBKDF2-SHA256-600000" || backup.cipher !== "AES-256-GCM" ||
          !backup.envelope || typeof backup.payload !== "string") {
        throw new Error("Formato de cópia não reconhecido ou versão incompatível.");
      }
      const exportKey = await window.MEtanoiaAccountCrypto.openEnvelope(EXPORT_USER_ID, passphrase, backup.envelope);
      let restored;
      try {
        const bytes = await window.MEtanoiaAccountCrypto.decryptRecord(
          EXPORT_USER_ID, EXPORT_RECORD_ID, EXPORT_DIMENSION, EXPORT_TYPE, exportKey, backup.payload
        );
        restored = validateData(JSON.parse(new TextDecoder().decode(bytes)));
      } finally {
        exportKey.fill(0);
      }
      const encrypted = await window.MEtanoiaAccountCrypto.encryptRecord(
        USER_ID, RECORD_ID, DIMENSION, RECORD_TYPE, key, JSON.stringify(restored)
      );
      const previous = await read(db, "app-state");
      await write(db, { "previous-app-state": previous, "app-state": encrypted });
      data = restored;
      return restored;
    }

    return Object.freeze({
      data,
      save,
      exportBackup,
      restoreBackup,
      requestPassphrase: passphraseGate
    });
  }

  window.MEtanoiaLocalVault = Object.freeze({ open, version: 1 });
})();
