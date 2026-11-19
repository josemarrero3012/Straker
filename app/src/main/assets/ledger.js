/* Todos los importes se contabilizan en céntimos. */
(() => {
  'use strict';
  const statuses = {pending:'Pendiente', won:'Ganada', lost:'Perdida', void:'Nula', cash:'Cash out'};
  const kinds = {opening:'Saldo inicial', deposit:'Ingreso', internal:'Traspaso', transfer:'Transferencia', withdrawal:'Retirada'};
  const limit = 99999999999;
  const insist = (condition, message) => { if (!condition) throw new Error(message); };
  const uid = () => Array.from(crypto.getRandomValues(new Uint32Array(4)), n => n.toString(16).padStart(8,'0')).join('');
  const now = () => new Date().toISOString();
  const empty = () => ({version:1, accounts:[], bets:[], moves:[]});
  function decimal(value, digits, label) {
    const raw = String(value ?? '').trim().replace(',', '.');
    insist(new RegExp('^\\d{1,10}(?:\\.\\d{1,' + digits + '})?$').test(raw), 'Revisa ' + label + '. Usa hasta ' + digits + ' decimales.');
    const [whole, fraction = ''] = raw.split('.');
    const n = Number(whole) * 10 ** digits + Number(fraction.padEnd(digits, '0'));
    insist(Number.isSafeInteger(n) && n <= limit, 'El importe es demasiado alto.');
    return n;
  }
  function label(value, max, required = true) {
    const result = String(value ?? '').trim();
    insist(!required || result.length, 'Completa los campos obligatorios.');
    insist(result.length <= max, 'El texto supera el límite de caracteres.');
    return result;
  }
  function payout(b) {
    if (b.status === 'won') return Math.floor((b.stake * b.odds + 500) / 1000);
    if (b.status === 'cash') return b.cash;
    return b.status === 'void' ? b.stake : 0;
  }
  function balances(s) {
    const totals = Object.fromEntries(s.accounts.map(a => [a.id, 0]));
    for (const m of s.moves) {
      if (m.from) totals[m.from] -= m.amount;
      if (m.to) totals[m.to] += m.amount;
    }
    for (const b of s.bets) totals[b.account] += payout(b) - b.stake;
    return totals;
  }
  function account(s, id) {
    const value = s.accounts.find(a => a.id === id);
    insist(value, 'Selecciona una cuenta válida.');
    return value;
  }
  function createAccount(s, f) {
    const name = label(f.name, 40), bookmaker = f.type === 'book' ? label(f.bookmaker, 40) : '';
    insist(['book','bank'].includes(f.type), 'Selecciona el tipo de cuenta.');
    insist(!s.accounts.some(a => a.name.toLocaleLowerCase('es') === name.toLocaleLowerCase('es')), 'Ya existe una cuenta con ese nombre.');
    const amount = decimal(f.amount || '0', 2, 'el saldo inicial');
    const a = {id:uid(), name, bookmaker, type:f.type, at:now()};
    s.accounts.push(a);
    if (amount) s.moves.push({id:uid(), kind:'opening', amount, from:null, to:a.id, at:a.at, note:''});
    return a;
  }
  function addMove(s, f) {
    insist(['deposit','internal','transfer','withdrawal'].includes(f.kind), 'Selecciona un movimiento.');
    const from = f.kind === 'deposit' ? null : account(s,f.from).id;
    const to = f.kind === 'withdrawal' ? null : account(s,f.to).id;
    insist(from !== to, 'Elige una cuenta de destino distinta.');
    const amount = decimal(f.amount,2,'el importe');
    insist(amount > 0, 'El importe debe ser mayor que cero.');
    insist(!from || balances(s)[from] >= amount, 'Saldo insuficiente en la cuenta de origen.');
    s.moves.push({id:uid(), kind:f.kind, from, to, amount, note:label(f.note,120,false), at:now()});
  }
  function addBet(s, f) {
    const a = account(s,f.account);
    insist(a.type === 'book', 'Elige una banca de apuestas.');
    const stake = decimal(f.stake,2,'el importe'), odds = decimal(f.odds,3,'la cuota');
    insist(stake > 0, 'El importe debe ser mayor que cero.');
    insist(odds >= 1000, 'La cuota debe ser igual o superior a 1.');
    insist(Number.isSafeInteger(stake * odds + 500) && (stake * odds / 1000) <= limit, 'El retorno supera el límite admitido.');
    insist(balances(s)[a.id] >= stake, 'Saldo insuficiente en esta banca.');
    const b = {id:uid(), account:a.id, event:label(f.event,80), pick:label(f.pick,100,false), sport:label(f.sport,30), stake, odds, at:now(), status:'pending', cash:0};
    s.bets.push(b);
    return b;
  }
  function settle(s, id, status, amount) {
    const b = s.bets.find(item => item.id === id);
    insist(b && Object.prototype.hasOwnProperty.call(statuses,status), 'Selecciona un estado válido.');
    const cash = status === 'cash' ? decimal(amount,2,'el retorno del cash out') : 0;
    const after = {...b, status, cash};
    insist(balances(s)[b.account] - payout(b) + payout(after) >= 0, 'Este cambio dejaría la banca en negativo. Revisa su saldo.');
    Object.assign(b, {status,cash});
  }
  function summary(s) {
    const closed = s.bets.filter(b => b.status !== 'pending');
    return {
      total:Object.values(balances(s)).reduce((a,b) => a+b,0),
      risk:s.bets.filter(b => b.status === 'pending').reduce((n,b) => n+b.stake,0),
      profit:closed.reduce((n,b) => n+payout(b)-b.stake,0),
      pending:s.bets.length-closed.length
    };
  }
  globalThis.Ledger = {empty, statuses, kinds, balances, payout, createAccount, addMove, addBet, settle, summary};
})();
