import { Component, OnInit } from '@angular/core';
import { M1, Order, Stock } from './stock';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    styleUrls: ['app.component.scss']
})
export class AppComponent implements OnInit {
    private ws: WebSocket;
    private messages: Array<string> = [];
    private m1s$: Observable<M1[]>;

    stocks: Array<Stock> = [];

    constructor(private httpClient: HttpClient) {
    }

    ngOnInit() {
        this.httpClient.get<any>('/clear-hash').subscribe();
        this.m1s$ = this.httpClient.get<M1[]>('/m1');
        setTimeout(() => { this.fetchM1() }, 1904)
    }

    private fetchM1() {
        this.m1s$.subscribe(
                m1arr => {
                    const _stocks = this.stocks.slice(0);
                    m1arr.forEach((m1: M1) => {
                        const i = _stocks.findIndex(stock => stock.name === m1.symbol);
                        if (i >= 0) _stocks.splice(i, 1);
                        _stocks.push({
                            name: m1.symbol,
                            ltp: m1.ltp,
                            quantity: m1.quantity,
                            orders: m1.orders.map(order => {
                                return {
                                    createdAt: order.createdAt.replace('T', ' '),
                                    id: order.id,
                                    side: order.side,
                                    price: order.price,
                                    state: order.state,
                                    quantity: order.quantity,
                                    matchId: order.matchId ? (parseInt(order.matchId.replace('-', ''), 16) % (36*36)).toString(36) : ''
                                }
                            }),
                            doneHidden: false,
                            stats: m1.stats,
                            shouldBuy: m1.shouldBuy,
                            shouldSell: m1.shouldSell
                        });
                    });
                    _stocks.sort((a, b) => {
                        if (a.quantity === 0) {
                            if (b.quantity === 0) return (a.name < b.name) ? -1 : 1;
                            else return 1;
                        } else {
                            if (b.quantity === 0) return -1;
                            else return (a.name < b.name) ? -1 : 1;
                        }
                    });
                    this.stocks = _stocks;
                    setTimeout(() => { this.fetchM1() }, 4567)
                }
        )
    }

    calculateCssClasses(symbol: string, o: Order): String {
        let css = '';
        if (o.matchId) css = css + ' done';
        if (o.state !== 'filled' /* queued or confirmed */) css = css + ' notfilled';
        if (o.side === 'buy') css = css + ' buy';
        else if (o.side === 'sell') css = css + ' sell';
        return css;
    }

    hideShow(symbol: string, hide: boolean) {
        this.stocks.find(stock => stock.name === symbol).doneHidden = hide
    }

    cancelOrder(orderId: string) {
        this.httpClient.get<any>('/cancel/' + orderId).subscribe(
                value => console.log(`cancel order ${orderId} ${value}`)
        )
    }
}
