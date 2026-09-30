window.snowyBotRunning = true;
window.__snowybotStopRequested = false;
window.__snowybotWatchdogReloadPending = false;
window.__snowybotRuntimeFailureReported = false;
window.addEventListener('error', function(event) {
    if (!window.snowyBotRunning || window.__snowybotRuntimeFailureReported) return;
    window.__snowybotRuntimeFailureReported = true;
    window.snowyBotRunning = false;
    window.__snowybotStopRequested = true;
    if (window.AndroidBridge) window.AndroidBridge.onSnowyBotStopped('runtime error: ' + String(event.message || event.error || 'unknown error'));
});
window.addEventListener('unhandledrejection', function(event) {
    if (!window.snowyBotRunning || window.__snowybotRuntimeFailureReported) return;
    window.__snowybotRuntimeFailureReported = true;
    window.snowyBotRunning = false;
    window.__snowybotStopRequested = true;
    if (window.AndroidBridge) window.AndroidBridge.onSnowyBotStopped('unhandled promise rejection: ' + String(event.reason || 'unknown reason'));
});
console.log('[Bot] bundled source loaded; entrypoint starting.');

// Chromium WebView does not guarantee Blob Worker/WebAudio availability. Use native timers
// as the predictable foreground fallback; a background WebView can still be throttled.
if (typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame = function(callback) { return setTimeout(callback, 1000 / 60); };
}
let bgTimerWorker = null;
let timerCallbacks = new Map();
let nextTimerId = 1;
try {
    const workerScript = `
        let timerStore = new Map();
        self.onmessage = function(e) {
            const data = e.data;
            if (data.cmd === 'setTimeout') {
                const tid = data.id;
                const timer = setTimeout(function() {
                    self.postMessage({ type: 'timeout', id: tid }); timerStore.delete(tid);
                }, data.delay);
                timerStore.set(tid, timer);
            } else if (data.cmd === 'clearTimeout') {
                const timer = timerStore.get(data.id);
                if (timer) { clearTimeout(timer); timerStore.delete(data.id); }
            }
        };
    `;
    if (typeof Worker === 'function' && typeof Blob === 'function' && URL.createObjectURL) {
        bgTimerWorker = new Worker(URL.createObjectURL(new Blob([workerScript], { type: 'application/javascript' })));
        console.log('[Bot] timer Worker initialized.');
    } else console.warn('[Bot] Worker unavailable; using WebView setTimeout fallback.');
    if (bgTimerWorker) bgTimerWorker.onmessage = function(e) {
        if (e.data.type === 'timeout') {
            const callback = timerCallbacks.get(e.data.id);
            if (callback) { timerCallbacks.delete(e.data.id); callback(); }
        }
    };
} catch (err) {
    console.warn('[Bot] Worker initialization failed; using WebView setTimeout fallback:', err && (err.message || err));
    bgTimerWorker = null;
}
function pauseExecution(delayMilliseconds) {
    return new Promise((resolve) => {
        let resolved = false;
        function done() { if (!resolved) { resolved = true; resolve(); } }
        try {
            if (window.snowyAudioCtx && typeof window.snowyAudioCtx.currentTime === 'number') {
                if (window.snowyAudioCtx.state === 'suspended') window.snowyAudioCtx.resume().catch(function() {});
                const targetTime = window.snowyAudioCtx.currentTime + delayMilliseconds / 1000;
                const audioCheckInterval = setInterval(function() {
                    if (window.snowyAudioCtx && window.snowyAudioCtx.currentTime >= targetTime) {
                        clearInterval(audioCheckInterval); done();
                    }
                }, 10);
            }
        } catch (e) { console.warn('[Bot] WebAudio timer unavailable; setTimeout fallback active:', e && (e.message || e)); }
        setTimeout(done, delayMilliseconds);
        if (bgTimerWorker) {
            try {
                const tid = nextTimerId++;
                timerCallbacks.set(tid, done);
                bgTimerWorker.postMessage({ cmd: 'setTimeout', id: tid, delay: delayMilliseconds });
            } catch (e) { done(); }
        }
    });
}
function saveState() {
    const botState = {
        walletStash, startingPocketChange, tinyPeanutSize, backupPeanut, tenPeanuts, areWeRichYet,
        oopsieCounter, previousWalletState, oldTicketStub, shinyNewTicket, totalSessionWins,
        totalSessionLosses, baseWinReference, baseLossReference, currentWagerAmount,
        previousWagerAmount, luckyCoinFlip, checkpointJuice, wobbleFactor, safetyCheckpoint
    };
    localStorage.setItem('snowybotbackup', JSON.stringify(botState));
}
function loadState() {
    const saved = localStorage.getItem('snowybotbackup');
    if (!saved) return null;
    try { return JSON.parse(saved); }
    catch (error) { console.error('[ERROR] Failed to parse snowybotbackup state.', error); return null; }
}
const savedState = loadState();
const startingPocketChange = savedState ? savedState.startingPocketChange : Number(shakeThePiggyBank());
const tinyPeanutSize = savedState ? savedState.tinyPeanutSize : Number((startingPocketChange / 1440000).toFixed(8));
const backupPeanut = savedState ? savedState.backupPeanut : Number(tinyPeanutSize);
const tenPeanuts = savedState ? savedState.tenPeanuts : Number(tinyPeanutSize * 10);
var walletStash = savedState ? savedState.walletStash : startingPocketChange;
var areWeRichYet = savedState ? savedState.areWeRichYet : false;
var oopsieCounter = savedState ? savedState.oopsieCounter : 0;
var previousWalletState = savedState ? savedState.previousWalletState : Number(parseFloat(walletStash));
var oldTicketStub = savedState ? savedState.oldTicketStub : 0;
var shinyNewTicket = savedState ? savedState.shinyNewTicket : 0;
let totalSessionWins = savedState ? savedState.totalSessionWins : Number(countTheHappyWins());
let totalSessionLosses = savedState ? savedState.totalSessionLosses : Number(countTheSadLosses());
let baseWinReference = savedState ? savedState.baseWinReference : Number(parseFloat(totalSessionWins));
let baseLossReference = savedState ? savedState.baseLossReference : Number(parseFloat(totalSessionLosses));
let currentWagerAmount = savedState ? savedState.currentWagerAmount : backupPeanut;
let previousWagerAmount = savedState ? savedState.previousWagerAmount : Number(parseFloat(currentWagerAmount));
var luckyCoinFlip = savedState ? savedState.luckyCoinFlip : 0;
var checkpointJuice = savedState ? savedState.checkpointJuice : parseFloat(startingPocketChange);
var wobbleFactor = savedState ? savedState.wobbleFactor : 1;
var safetyCheckpoint = savedState ? savedState.safetyCheckpoint : parseFloat(Math.floor(walletStash / (tinyPeanutSize * 10)) * (tinyPeanutSize * 10));
function inspectRollOutcome() {
    const table = document.getElementById('me');
    const target = table && table.firstChild && table.firstChild.lastChild && table.firstChild.lastChild.firstChild && table.firstChild.lastChild.firstChild.children[7];
    if (!target) return -1;
    const value = Number(String(target.innerText || '').replace(/,/g, ''));
    return Number.isFinite(value) ? value : -1;
}
function countTheHappyWins() {
    const el = document.getElementById('wins');
    return el && el.innerText ? Number(el.innerText.replace(/,/g, '')) : 0;
}
function countTheSadLosses() {
    const el = document.getElementById('losses');
    return el && el.innerText ? Number(el.innerText.replace(/,/g, '')) : 0;
}
function fetchLatestWagerId() {
    const table = document.getElementById('me');
    const row = table && table.firstChild && table.firstChild.lastChild && table.firstChild.lastChild.firstChild && table.firstChild.lastChild.firstChild.children[5];
    if (!row) return NaN;
    const id = parseInt(String(row.innerText || '').replace(/,/g, ''), 10);
    return Number.isFinite(id) && id > 0 ? id : NaN;
}
function shakeThePiggyBank() {
    const el = document.getElementById('pct_balance');
    if (!el) return 0;
    const raw = ('value' in el && el.value) ? el.value : (el.innerText || el.textContent || '');
    const parsed = Number.parseFloat(String(raw).replace(/,/g, ''));
    return Number.isFinite(parsed) ? Number(parsed.toFixed(8)) : 0;
}
function inspectCurrentWalletBalance() {
    const balance = document.getElementById('pct_balance');
    const raw = balance ? ((('value' in balance && balance.value) ? balance.value : (balance.innerText || balance.textContent || '')).trim()) : '';
    const normalized = raw.replace(/[^0-9+.-]/g, '');
    const amount = Number(normalized);
    return normalized && Number.isFinite(amount) && amount > 0 ? amount : null;
}
async function configureWinOdds(chanceValue = 49.5) {
    const input = document.getElementById('pct_chance');
    if (!input) throw new Error('Placement rejected: #pct_chance is missing');
    const formatted = Number(chanceValue).toFixed(4);
    input.value = formatted;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    if (input.value !== formatted) throw new Error('Placement rejected: chance value did not persist');
}
async function applyStakeAmount(stakeValue) {
    const input = document.getElementById('pct_bet');
    if (!input) throw new Error('Placement rejected: #pct_bet is missing');
    const stake = Number(stakeValue);
    if (!Number.isFinite(stake) || stake <= 0) throw new Error('Placement rejected: stake must be positive and finite');
    const formatted = stake.toFixed(8);
    if (Number(formatted) <= 0) throw new Error('Placement rejected: stake is below 8-decimal precision');
    input.value = formatted;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    if (Number(input.value) !== Number(formatted)) throw new Error('Placement rejected: increased wager did not persist in #pct_bet');
    console.log('[Bot] Verified #pct_bet=' + formatted);
    return Number(formatted);
}
async function executePlacementRoutine(stake, chance = 49.5) {
    const minButton = document.getElementById('b_min');
    const rollButton = document.getElementById('a_lo');
    if (!minButton || !rollButton) throw new Error('Placement rejected: required #b_min/#a_lo control is missing');
    minButton.click();
    await pauseExecution(1);
    const formatted = await applyStakeAmount(stake);
    await configureWinOdds(chance);
    const input = document.getElementById('pct_bet');
    const balance = document.getElementById('pct_balance');
    const rawBalance = balance ? (('value' in balance && balance.value) ? balance.value : (balance.innerText || balance.textContent || '')) : '';
    const numericBalance = Number(String(rawBalance).replace(/[^0-9+.-]/g, ''));
    const minAmount = Number(input.min || 0);
    const maxAmount = Number(input.max || numericBalance);
    const amount = Number(formatted);
    if (minAmount > 0 && amount < minAmount) throw new Error('Placement rejected: stake ' + formatted + ' is below site minimum ' + minAmount);
    if (Number.isFinite(maxAmount) && maxAmount > 0 && amount > maxAmount) throw new Error('Placement rejected: stake ' + formatted + ' exceeds available/site maximum ' + maxAmount);
    if (rollButton.disabled) throw new Error('Placement rejected: #a_lo is disabled');
    console.log('[Bot] Dispatching #a_lo with verified #pct_bet=' + formatted);
    rollButton.click();
    return formatted;
}
let betPlacementInFlight = false;
let placementAccepted = true;
let lastSubmittedStake = null;
let lastSubmittedWagerId = null;
let placementCompletionTimer = null;
let watchdogReloading = false;
function stopBalanceWatchdog() {
    watchdogReloading = true;
    window.__snowybotStopRequested = true;
    window.snowyBotRunning = false;
}
function settleInFlightPlacement(reason) {
    if (!betPlacementInFlight) return false;
    placementAccepted = true;
    betPlacementInFlight = false;
    if (placementCompletionTimer !== null) clearTimeout(placementCompletionTimer);
    placementCompletionTimer = null;
    console.log('[Bot] Placement settled (' + reason + '); stake=' + lastSubmittedStake);
    lastSubmittedStake = null;
    lastSubmittedWagerId = null;
    return true;
}
function awaitPlacementCompletion(stake, priorId) {
    if (placementCompletionTimer !== null) clearTimeout(placementCompletionTimer);
    placementAccepted = false;
    betPlacementInFlight = true;
    lastSubmittedStake = stake;
    lastSubmittedWagerId = priorId;
    placementCompletionTimer = setTimeout(function() {
        if (betPlacementInFlight) console.warn('[Bot] Placement is still pending; balance watchdog independently governs recovery.');
    }, 120000);
}
function observePlacementSettlement() {
    if (!betPlacementInFlight) return;
    const id = fetchLatestWagerId();
    if (Number.isFinite(id) && Number.isFinite(lastSubmittedWagerId) && id > lastSubmittedWagerId) {
        settleInFlightPlacement("completed ticket " + id);
    }
}
window.__snowybotSettlePlacementForTest = settleInFlightPlacement;
window.__snowybotPlacementStateForTest = function() { return { inFlight: betPlacementInFlight, accepted: placementAccepted, stake: lastSubmittedStake }; };
window.__snowybotTryNextBetForTest = function(progressedAmount, priorId) {
    if (!placementAccepted || betPlacementInFlight) return false;
    placementAccepted = false;
    awaitPlacementCompletion(progressedAmount, priorId);
    return true;
};
window.__snowybotStopAfterWatchdog = function() {
    stopBalanceWatchdog();
    if (placementCompletionTimer !== null) clearTimeout(placementCompletionTimer);
    placementCompletionTimer = null;
    betPlacementInFlight = false;
    placementAccepted = true;
};
setInterval(function() { if (window.snowyBotRunning) shakeThePiggyBank(); }, 3000);
function resolveProgressedWager(computedWager) {
    const normalized = Number(Number(computedWager).toFixed(8));
    if (!Number.isFinite(normalized) || normalized <= 0) throw new Error('Progressed wager must be positive and finite');
    currentWagerAmount = normalized;
    return normalized;
}
async function calculateNextProgressionStep(incomingWager) {
    walletStash = shakeThePiggyBank();
    currentWagerAmount = parseFloat(incomingWager);
    if (walletStash >= safetyCheckpoint + (tinyPeanutSize * 10) * wobbleFactor) {
        currentWagerAmount = backupPeanut;
        wobbleFactor = 1;
        checkpointJuice = parseFloat(Math.floor(walletStash / (tinyPeanutSize * 10)) * (tinyPeanutSize * 10));
        safetyCheckpoint = parseFloat(Math.floor(walletStash / (tinyPeanutSize * 10)) * (tinyPeanutSize * 10));
    }
    if (currentWagerAmount < backupPeanut * 1.5 && walletStash > checkpointJuice + currentWagerAmount * 6.9) {
        currentWagerAmount *= 2; checkpointJuice = parseFloat(walletStash);
    }
    if (currentWagerAmount < backupPeanut * 1.5 && walletStash < checkpointJuice - currentWagerAmount * 2.9) {
        currentWagerAmount *= 2; checkpointJuice = parseFloat(walletStash);
    }
    if (currentWagerAmount > backupPeanut * 1.5 && walletStash > checkpointJuice + currentWagerAmount * 4.9) {
        currentWagerAmount *= 2; checkpointJuice = parseFloat(walletStash);
    }
    if (currentWagerAmount > backupPeanut * 1.5 && walletStash < checkpointJuice - currentWagerAmount * 4.9) {
        currentWagerAmount *= 2; wobbleFactor = 0; checkpointJuice = parseFloat(walletStash);
    }
    return Number(Number(currentWagerAmount).toFixed(8));
}
let lastLoggedWagerId = 0;
function logBetInfo(amount, profit, wagerId) {
    if (wagerId && wagerId > 0 && wagerId === lastLoggedWagerId) return;
    if (wagerId && wagerId > 0) lastLoggedWagerId = wagerId;
    console.log('Bet: ' + Number(amount).toFixed(8) + ' | Profit: ' + Number(profit).toFixed(8));
}
async function runPrimaryBettingLoop() {
    let loopIteration = 0;
    console.log('[Bot] runPrimaryBettingLoop entered.');
    while (window.snowyBotRunning && !window.__snowybotStopRequested) {
        loopIteration++;
        if (loopIteration === 1 || loopIteration % 100 === 0) console.log('[Bot] loop iteration=' + loopIteration + ' balance=' + shakeThePiggyBank());
        try {
            walletStash = shakeThePiggyBank();
            shinyNewTicket = fetchLatestWagerId();
            observePlacementSettlement();
            if (placementAccepted && !betPlacementInFlight && ((shinyNewTicket > oldTicketStub) || oopsieCounter === 0)) {
                const nextBet = resolveProgressedWager(await calculateNextProgressionStep(previousWagerAmount));
                currentWagerAmount = nextBet;
                if (walletStash >= 144000) {
                    console.log('[System] TARGET REACHED (' + walletStash + '). Halting execution.');
                    localStorage.removeItem('snowybotbackup');
                    window.snowyBotRunning = false;
                    window.__snowybotStopRequested = true;
                    if (window.AndroidBridge) window.AndroidBridge.onSnowyBotStopped('target reached');
                    return;
                }
                const roll = inspectRollOutcome();
                if (roll >= 0 && roll < 49.5) luckyCoinFlip = 1;
                else if (roll >= 49.5) luckyCoinFlip = 0;
                totalSessionWins = countTheHappyWins();
                totalSessionLosses = countTheSadLosses();
                if (oopsieCounter === 0 || shinyNewTicket > oldTicketStub) {
                    const first = oopsieCounter === 0;
                    logBetInfo(currentWagerAmount, walletStash - startingPocketChange, shinyNewTicket);
                    console.log('[Bot] ' + (first ? 'First' : 'Follow-up') + ' placement sequence stake=' + currentWagerAmount);
                    const priorId = shinyNewTicket;
                    placementAccepted = false;
                    let placementResult;
                    try { placementResult = await executePlacementRoutine(nextBet, 49.5); }
                    catch (placementError) { placementAccepted = true; throw placementError; }
                    const placed = Number(placementResult);
                    if (!Number.isFinite(placed) || placed <= 0 || placed !== nextBet) {
                        placementAccepted = true;
                        throw new Error('Placed stake does not match authoritative progression amount');
                    }
                    awaitPlacementCompletion(placed, priorId);
                    previousWagerAmount = nextBet;
                    if (!first) {
                        if (luckyCoinFlip === 1) baseWinReference++;
                        else baseLossReference++;
                    }
                    previousWalletState = Number(walletStash);
                    oldTicketStub = Number.isFinite(shinyNewTicket) ? shinyNewTicket : 0;
                    oopsieCounter++;
                    saveState();
                }
            }
        } catch (error) {
            console.error('[Error] Execution error recovered: ' + (error && (error.message || error)));
            window.snowyBotRunning = false;
            window.__snowybotStopRequested = true;
            if (window.AndroidBridge) window.AndroidBridge.onSnowyBotStopped('runtime loop error: ' + String(error && error.message || error));
            return;
        }
        await pauseExecution(50);
    }
    if (window.__snowybotStopRequested || !window.snowyBotRunning) console.log('[Bot] stop flag observed; betting loop exited without another placement.');
}
function inspectSessionAuthentication() {
    const balance = document.getElementById('pct_balance');
    const visible = (element) => {
        if (!element) return false;
        const style = window.getComputedStyle ? window.getComputedStyle(element) : null;
        const rect = element.getBoundingClientRect ? element.getBoundingClientRect() : null;
        return (!style || (style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0')) && (!rect || (rect.width > 0 && rect.height > 0));
    };
    const raw = balance ? ((('value' in balance && balance.value) ? balance.value : (balance.innerText || balance.textContent || '')).trim()) : '';
    const normalized = raw.replace(/[^0-9+.-]/g, '');
    const amount = Number(normalized);
    const authenticated = !!(balance && visible(balance) && normalized && isFinite(amount) && amount > 0);
    return { authenticated, reason: authenticated ? 'positive parsed #pct_balance' : !balance ? '#pct_balance is missing' : !visible(balance) ? '#pct_balance is hidden' : !normalized || !isFinite(amount) ? '#pct_balance is not parseable' : 'balance is not positive', evidence: 'balancePresent=' + !!balance + ', balanceVisible=' + visible(balance) + ', balanceRaw="' + raw + '"' };
}
const currentWalletBalance = inspectCurrentWalletBalance();
if (currentWalletBalance !== null) {
    window.__snowybotLoginConfirmed = true;
    if (window.__snowybotLoginStabilizationTimer) {
        clearTimeout(window.__snowybotLoginStabilizationTimer);
        window.__snowybotLoginStabilizationTimer = null;
    }
    if (window.__snowybotLoginBalanceTimeout) {
        clearTimeout(window.__snowybotLoginBalanceTimeout);
        window.__snowybotLoginBalanceTimeout = null;
    }
    if (window.__snowybotLoginBalancePoll) {
        clearInterval(window.__snowybotLoginBalancePoll);
        window.__snowybotLoginBalancePoll = null;
    }
}
const sessionAuth = inspectSessionAuthentication();
if (!sessionAuth.authenticated) {
    window.snowyBotRunning = false;
    console.error('[Bot] Authentication not confirmed; refusing runtime. reason=' + sessionAuth.reason + '; evidence: ' + sessionAuth.evidence);
} else {
    console.log('[Bot] Session authentication confirmed from current DOM; ' + sessionAuth.evidence);
    void (async function initializeBotEngine() {
        try {
            console.log('[Bot] initializeBotEngine invoked; starting asynchronous loop.');
            await runPrimaryBettingLoop();
            console.log('[Bot] betting loop exited; running=' + window.snowyBotRunning + ' stopRequested=' + window.__snowybotStopRequested);
        } catch (error) {
            console.error('[Bot] engine error: ' + (error && (error.stack || error)));
            window.snowyBotRunning = false;
        }
    })();
}
