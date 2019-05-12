import { Order, Stock } from './stock';

export class Util {
    static cloneOrder(o: Order): Order {
        return {
            createdAt: o.createdAt,
            id: o.id,
            side: o.side,
            price: o.price,
            state: o.state,
            quantity: o.quantity,
            matchId: o.matchId
        };
    }

    static cloneStock(s: Stock): Stock {
        return {
            name: s.name,
            ltp: s.ltp,
            quantity: s.quantity,
            orders: (s.orders !== null && s.orders.length > 0) ? s.orders.slice(0) : [],
            doneHidden: s.doneHidden,
            stats: s.stats
        }
    }
}