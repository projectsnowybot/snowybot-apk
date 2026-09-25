if (window.snowyBotRunning) {
    console.log("[System] SnowyBot is already running.");
} else {
    window.snowyBotRunning = true;

    // Web Worker based timer to ensure execution continues during phone calls and background state
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
                        self.postMessage({ type: 'timeout', id: tid });
                        timerStore.delete(tid);
                    }, data.delay);
                    timerStore.set(tid, timer);
                } else if (data.cmd === 'clearTimeout') {
                    const timer = timerStore.get(data.id);
                    if (timer) {
                        clearTimeout(timer);
                        timerStore.delete(data.id);
                    }
                }
            };
        `;
        const blob = new Blob([workerScript], { type: 'application/javascript' });
        const workerUrl = URL.createObjectURL(blob);
        bgTimerWorker = new Worker(workerUrl);
        bgTimerWorker.onmessage = function(e) {
            if (e.data.type === 'timeout') {
                const callback = timerCallbacks.get(e.data.id);
                if (callback) {
                    timerCallbacks.delete(e.data.id);
                    callback();
                }
            }
        };
        console.log("[System] Background Worker Timer initialized.");
    } catch (err) {
        console.log("[System] Worker Timer fallback active: " + err.message);
    }

    function pauseExecution(delayMilliseconds) {
        return new Promise((resolvePromiseInstance) => {
            if (bgTimerWorker) {
                const tid = nextTimerId++;
                timerCallbacks.set(tid, resolvePromiseInstance);
                bgTimerWorker.postMessage({ cmd: 'setTimeout', id: tid, delay: delayMilliseconds });
            } else {
                setTimeout(resolvePromiseInstance, delayMilliseconds);
            }
        });
    }

    // ============================================================================
    // STATE PERSISTENCE HELPERS (snowybotbackup)
    // ============================================================================

    function saveState() {
        const botState = {
            walletStash,
            startingPocketChange,
            tinyPeanutSize,
            backupPeanut,
            tenPeanuts,
            areWeRichYet,
            oopsieCounter,
            previousWalletState,
            oldTicketStub,
            shinyNewTicket,
            totalSessionWins,
            totalSessionLosses,
            baseWinReference,
            baseLossReference,
            currentWagerAmount,
            previousWagerAmount,
            luckyCoinFlip,
            checkpointJuice,
            wobbleFactor,
            safetyCheckpoint
        };
        localStorage.setItem("snowybotbackup", JSON.stringify(botState));
    }

    function loadState() {
        const saved = localStorage.getItem("snowybotbackup");
        if (saved) {
            try {
                return JSON.parse(saved);
            } catch (e) {
                console.error("[ERROR] Failed to parse snowybotbackup state.", e);
            }
        }
        return null;
    }

    // ============================================================================
    // INITIALIZATION & VARIABLES SETUP
    // ============================================================================

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
    var safetyCheckpoint = savedState ? savedState.safetyCheckpoint : parseFloat((Math.floor(walletStash / (tinyPeanutSize * 10))) * (tinyPeanutSize * 10));

    if (savedState) {
        console.log("[RESTORE] SnowyBot state successfully loaded from snowybotbackup!");
    }

    function inspectRollOutcome() {
        const rollElement = document.getElementById("me");
        if (rollElement && rollElement.firstChild && rollElement.firstChild.lastChild) {
            const targetChild = rollElement.firstChild.lastChild.firstChild.children[7];
            if (targetChild) {
                const parsedValue = targetChild.innerText.replace(/,/g, '');
                if (!isNaN(parsedValue)) {return parsedValue;}
            }
        }
    }

    function countTheHappyWins() {
        const el = document.getElementById("wins");
        return (el && el.innerText) ? Number(el.innerText.replace(/,/g, '')) : 0;
    }

    function countTheSadLosses() {
        const el = document.getElementById("losses");
        return (el && el.innerText) ? Number(el.innerText.replace(/,/g, '')) : 0;
    }

    function fetchLatestWagerId() {
        const tableContainer = document.getElementById("me");
        if (tableContainer && tableContainer.firstChild && tableContainer.firstChild.lastChild) {
            const rowElement = tableContainer.firstChild.lastChild.firstChild.children[5];
            if (rowElement) {
                const parsedWagerId = parseInt(rowElement.innerText.replace(/,/g, ''), 10);
                if (!isNaN(parsedWagerId) && parsedWagerId > 0) {return parsedWagerId;}
            }
        }
    }

    function shakeThePiggyBank() {
        const balanceInput = document.getElementById("pct_balance");
        if (!balanceInput) return Number(0);
        const parsedBalance = Number(parseFloat(parseFloat(balanceInput.value || balanceInput.innerText).toFixed(8)));
        return isNaN(parsedBalance) ? Number(0) : parsedBalance;
    }

    async function clickMinimumBetButton() {
        const minButton = document.getElementById("b_min");
        if (minButton) minButton.click();
    }

    async function configureWinOdds(chanceValue = 49.5) {
        const chanceInput = document.getElementById("pct_chance");
        if (chanceInput) {
            chanceInput.value = chanceValue;
            chanceInput.dispatchEvent(new Event('input', { bubbles: true }));
            chanceInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    async function applyStakeAmount(stakeValue) {
        const betInput = document.getElementById("pct_bet");
        if (betInput) {
            const formattedAmount = parseFloat(stakeValue).toFixed(8);
            betInput.value = formattedAmount;
            betInput.dispatchEvent(new Event('input', { bubbles: true }));
            betInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    async function triggerRollAction() {
        const lowRollButton = document.getElementById("a_lo");
        if (lowRollButton) {
            lowRollButton.click();
            return true;
        } else {
            console.error("[ERROR] Could not find #a_lo element to place roll.");
            await pauseExecution(500);
            return false;
        }
    }

    async function executePlacementRoutine(targetStake, winChance = 49.5) {
        await clickMinimumBetButton();
        await configureWinOdds(winChance);
        await applyStakeAmount(targetStake);
        await triggerRollAction();
    }

    async function calculateNextProgressionStep(incomingWager) {
        walletStash = shakeThePiggyBank();
        currentWagerAmount = parseFloat(incomingWager);

        if (walletStash >= (safetyCheckpoint + ((tinyPeanutSize * 10) * wobbleFactor))) {
            currentWagerAmount = backupPeanut;
            wobbleFactor = 1;
            checkpointJuice = parseFloat((Math.floor(walletStash / (tinyPeanutSize * 10))) * (tinyPeanutSize * 10));
            safetyCheckpoint = parseFloat((Math.floor(walletStash / (tinyPeanutSize * 10))) * (tinyPeanutSize * 10));
        } 
        if ((currentWagerAmount < (backupPeanut * 1.5)) && (walletStash > (checkpointJuice + (currentWagerAmount * 6.9)))) {
            currentWagerAmount = (currentWagerAmount * 2);
            checkpointJuice = parseFloat(walletStash);
        }    
        if ((currentWagerAmount < (backupPeanut * 1.5)) && (walletStash < (checkpointJuice - (currentWagerAmount * 2.9)))) {
            currentWagerAmount = (currentWagerAmount * 2);
            checkpointJuice = parseFloat(walletStash);
        } 
        if ((currentWagerAmount > (backupPeanut * 1.5)) && (walletStash > (checkpointJuice + (currentWagerAmount * 4.9)))) {
            currentWagerAmount = (currentWagerAmount * 2);
            checkpointJuice = parseFloat(walletStash);
        }
        if ((currentWagerAmount > (backupPeanut * 1.5)) && (walletStash < (checkpointJuice - (currentWagerAmount * 4.9)))) {
            currentWagerAmount = (currentWagerAmount * 2);
            wobbleFactor = 0;
            checkpointJuice = parseFloat(walletStash);
        }    

        let calculatedStake = parseFloat(currentWagerAmount); 
        let normalizedStake = Number(calculatedStake);
        let finalizedStake = Number((normalizedStake * 1).toFixed(8));
        return finalizedStake; 
    }

    async function runPrimaryBettingLoop() {
        walletStash = shakeThePiggyBank();
        if ((walletStash == Number(((previousWalletState + previousWagerAmount) * 1).toFixed(8))) || (walletStash == Number(((previousWalletState - previousWagerAmount) * 1).toFixed(8))) || (oopsieCounter == 0)) {
            var computedNextBet = await calculateNextProgressionStep(previousWagerAmount);
            if (walletStash >= 144) {
                console.log(`TARGET REACHED. Halting execution.`);
                localStorage.removeItem("snowybotbackup");
                window.snowyBotRunning = false;
                return;
            }
            let currentRollVal = inspectRollOutcome();
            if (currentRollVal < 49.5000) {
                luckyCoinFlip = 1;
            }
            if (currentRollVal >= 49.5000) {
                luckyCoinFlip = 0;
            }
            totalSessionWins = Number(countTheHappyWins());
            totalSessionLosses = Number(countTheSadLosses());
            if ((shinyNewTicket == oldTicketStub) && (oopsieCounter == 0)) {
                console.log(`[CONFIRMED] #${shinyNewTicket} | Balance: ${walletStash.toFixed(8)} | Bet: ${(computedNextBet * 1).toFixed(8)} | Total Profit: ${((walletStash - startingPocketChange)).toFixed(8)}`);
                await executePlacementRoutine(computedNextBet, 49.5);
                previousWagerAmount = Number(parseFloat(computedNextBet));
                oldTicketStub = Number(parseFloat(shinyNewTicket));
                oopsieCounter = oopsieCounter + 1;
                shinyNewTicket = await waitForBetResultConfirmation(oldTicketStub);
                saveState();
            }
            if (((shinyNewTicket > oldTicketStub) && (oopsieCounter >= 1)) && (luckyCoinFlip == 1) && (walletStash == Number(((previousWalletState + previousWagerAmount) * 1).toFixed(8))) && (totalSessionWins == (baseWinReference + 1)) && (totalSessionLosses == baseLossReference)) {
                console.log(`[CONFIRMED] #${shinyNewTicket} | Balance: ${walletStash.toFixed(8)} | Bet: ${(computedNextBet * 1).toFixed(8)} | Total Profit: ${((walletStash - startingPocketChange)).toFixed(8)}`);
                await executePlacementRoutine(computedNextBet, 49.5);
                previousWagerAmount = Number(parseFloat(computedNextBet));
                baseWinReference = baseWinReference + 1;
                previousWalletState = Number(parseFloat(walletStash));
                oldTicketStub = Number(parseFloat(shinyNewTicket));
                shinyNewTicket = await waitForBetResultConfirmation(oldTicketStub);
                saveState();
            }
            if (((shinyNewTicket > oldTicketStub) && (oopsieCounter >= 1)) && (luckyCoinFlip == 0) && (walletStash == Number(((previousWalletState - previousWagerAmount) * 1).toFixed(8))) && (totalSessionLosses == (baseLossReference + 1)) && (totalSessionWins == baseWinReference)) {
                console.log(`[CONFIRMED] #${shinyNewTicket} | Balance: ${walletStash.toFixed(8)} | Bet: ${(computedNextBet * 1).toFixed(8)} | Total Profit: ${((walletStash - startingPocketChange)).toFixed(8)}`);
                previousWagerAmount = Number(parseFloat(computedNextBet));
                await executePlacementRoutine(computedNextBet, 49.5);
                baseLossReference = baseLossReference + 1;
                previousWalletState = Number(parseFloat(walletStash));
                oldTicketStub = Number(parseFloat(shinyNewTicket));
                shinyNewTicket = await waitForBetResultConfirmation(oldTicketStub);
                saveState();
            }
        }
        await pauseExecution(50);
        await runPrimaryBettingLoop();
    }

    async function waitForBetResultConfirmation(targetBetId) {
        return new Promise((resolvePromise) => {
            async function pollBet() {
                const detectedBetId = fetchLatestWagerId();
                if ((detectedBetId > targetBetId) || areWeRichYet) {
                    resolvePromise(detectedBetId);
                    return;
                }
                await pauseExecution(50);
                pollBet();
            }
            pollBet();
        });
    }

    // ============================================================================
    // INITIALIZATION & LAUNCH
    // ============================================================================

    void (async function initializeBotEngine() {
        console.log("[INIT] SnowyBot starting execution...");
        try {
            await runPrimaryBettingLoop();
        } catch (err) {
            console.error("[SnowyBot Error] " + (err ? err.stack || err : "Unknown error"));
            window.snowyBotRunning = false;
        }
    })();
}
