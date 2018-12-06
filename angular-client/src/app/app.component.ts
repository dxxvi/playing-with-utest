import { Component, OnDestroy, OnInit } from '@angular/core';
import { WebsocketService } from './websocket.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'div.app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  symbols: Array<string> = [];
  notices: Array<{uuid: string, message: string, level: string}> = [];
  private symbolFoundSubscription: Subscription;
  private noticeAddSubscription: Subscription;
  private noticeDeleteSubscription: Subscription;
  private positionMap: Map<string, number> = new Map<string, number>();

  constructor(private websocketService: WebsocketService) {
  }

  ngOnInit(): void {
    this.symbolFoundSubscription = this.websocketService.getSubject('SYMBOL_FOUND').asObservable().subscribe(
      value => {
        let isNewSymbol = false;
        const symbol = value.symbol;
        if (value.rest.startsWith('POSITION: ')) {
          const position = JSON.parse(value.rest.replace('POSITION: ', ''));
          if (position.quantity) {
            this.positionMap.set(symbol, position.quantity);
          }
          isNewSymbol = this.addSymbolIfNotExist(symbol, true);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { POSITION: position })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('FUNDAMENTAL: ')) {
          const fundamental = JSON.parse(value.rest.replace('FUNDAMENTAL: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { FUNDAMENTAL: fundamental })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('QUOTE: ')) {
          const quote = JSON.parse(value.rest.replace('QUOTE: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { QUOTE: quote })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('ORDERS: ')) {
          const orders = JSON.parse(value.rest.replace('ORDERS: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { ORDERS: orders })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('HISTORICAL_QUOTES: ')) {
          const hqs = JSON.parse(value.rest.replace('HISTORICAL_QUOTES: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { HISTORICAL_QUOTES: hqs })
          }, isNewSymbol ? 200 : 1);
        }
        else if (value.rest.startsWith('CURRENT_STATUS: ')) {
          const currentStatus = JSON.parse(value.rest.replace('CURRENT_STATUS: ', ''));
          isNewSymbol = this.addSymbolIfNotExist(symbol);
          setTimeout(() => {
            this.websocketService.sendMessageThroughSubject(symbol, { CURRENT_STATUS: currentStatus })
          }, isNewSymbol ? 200 : 1);
        }
        else {
          console.log(`Unknown message for ${symbol}: ${value.rest}`);
          this.notices.push({
            uuid: this.generateUUID(),
            message: `Unknown message for ${symbol}: ${value.rest}`,
            level: 'danger'
          });
        }
      }
    );

    this.noticeAddSubscription = this.websocketService.getSubject('NOTICE_ADD').asObservable().subscribe(value => {
      const i = value.indexOf(': ');
      if (i > 0) {
        const level = value.substring(0, i).toLowerCase();
        const message = value.substr(i + 2);
        this.notices.push({uuid: this.generateUUID(), message: message, level: level});
      }
      else {
        console.error(`Unknown notice format: ${value}`)
      }
    });

    this.noticeDeleteSubscription = this.websocketService.getSubject('NOTICE_DELETE').asObservable().subscribe(value => {
      this.removeNotice(value);
    });
  }

  ngOnDestroy(): void {
    this.symbolFoundSubscription.unsubscribe();
    this.noticeAddSubscription.unsubscribe();
    this.noticeDeleteSubscription.unsubscribe();
  }

  /**
   * @return true if this is a new symbol
   */
  addSymbolIfNotExist(symbol: string, sort = false): boolean {
    let result = false;
    let justSorted = false;
    if (!this.symbols.includes(symbol)) {
      this.symbols.push(symbol);
      this.symbols.sort((s1: string, s2: string) => this.sortSymbol(s1, s2));
      justSorted = true;
      result = true;
    }
    if (sort && !justSorted) {
      this.symbols.sort((s1: string, s2: string) => this.sortSymbol(s1, s2));
    }
    return result;
  }

  calculateNoticeClass(level: string) {
    const _level = level.toLowerCase();
    if (_level === 'primary') {
      return ['primary'];
    }
    else if (_level === 'danger') {
      return ['danger'];
    }
    else if (_level === 'info') {
      return ['info'];
    }
    else {
      return ['light'];
    }
  }

  removeNotice(uuid: string) {
    const i = this.notices.findIndex(v => v.uuid === uuid);
    if (i >= 0) {
      this.notices.splice(i, 1);
    }
  }

  removeSymbol(symbol: string) {
    const i = this.symbols.findIndex(e => e === symbol);
    if (i >= 0) this.symbols.splice(i, 1);
  }

  trackByFunction(i: number, symbol: string): string {
    return symbol;
  }

  trackByNoticeFunction(i: number, n: {uuid: string, message: string, level: string}): string {
    return n.uuid;
  }

  private generateUUID(): string {
    const s = function(): string {
      return Math.floor((1 + Math.random()) * 1679616).toString(36).substring(1);
    };
    return s() + '-' + s();
  }

  private havingShares(symb0l: string): boolean {
    return this.positionMap.has(symb0l) && this.positionMap.get(symb0l) > 0;
  }

  private sortSymbol(s1: string, s2: string) {
    const havingShares1 = this.havingShares(s1);
    const havingShares2 = this.havingShares(s2);
    if (havingShares1 && havingShares2) {
      return s1 > s2 ? 1 : (s1 < s2 ? -1 : 0);
    }
    else if (!havingShares1 && havingShares2) {
      return 1;
    }
    else if (havingShares1 && !havingShares2) {
      return -1;
    }
    else {
      return s1 > s2 ? 1 : (s1 < s2 ? -1 : 0);
    }
  }
}
