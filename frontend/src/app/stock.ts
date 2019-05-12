export interface Stock {
    name: string;
    ltp: number;
    quantity: number;
    orders: Array<Order>;
    doneHidden: boolean;
    stats: Stats;
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
    hl1m: number;
    ho1m: number;
    ol1m: number;
    hpc1m: number;
    pcl1m: number;
    h1m: number;
    l1m: number;
    hl3m: number;
    ho3m: number;
    ol3m: number;
    hpc3m: number;
    pcl3m: number;
    h3m: number;
    l3m: number;
}

export interface M1 {
    symbol: string,
    ltp: number,
    quantity: number,
    orders: Array<Order>,
    stats: Stats
}