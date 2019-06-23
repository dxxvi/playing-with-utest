export interface Stock {
    name: string;
    ltp: number;
    quantity: number;
    orders: Array<Order>;
    doneHidden: boolean;
    stats: Stats;
    shouldBuy: boolean;
    shouldSell: boolean;
}

export interface Order {
    createdAt: string;
    id: string;
    side: string;
    price: number;
    state: string;
    quantity: number;
    matchId: string;
}

export interface Stats {
    currl3m: number;
    hcurr3m: number;
    curro3m: number;
    ocurr3m: number;
    currpc3m: number;
    pccurr3m: number;
    h3m: number;
    l3m: number;
    currl1m: number;
    hcurr1m: number;
    curro1m: number;
    ocurr1m: number;
    currpc1m: number;
    pccurr1m: number;
    h1m: number;
    l1m: number;
    high: number;
    low: number;
    open: number;
    previousClose: number;
}

export interface M1 {
    symbol: string;
    ltp: number;
    quantity: number;
    orders: Array<Order>;
    stats: Stats;
    shouldBuy: boolean;
    shouldSell: boolean;
}