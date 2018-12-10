import { Component, ElementRef, OnDestroy, OnInit } from '@angular/core';
import { WebsocketService } from './websocket.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'div.configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  open = false;
  message: string = null;
  symbol: string = null;
  showStats: boolean = true;
  compound: {
    symbol: string,
    fundamental: {
      open: number,
      low: number,
      high: number,
      high_52_weeks: number,
      low_52_weeks: number,
      dividend_yield: string,
      description: string,
      year_found: number
    },
    quotes: Array<{
      beginsAt: string,
      closePrice: number,
      highPrice: number,
      interpolated: boolean,
      lowPrice: number,
      openPrice: number,
      session: string,
      volume: number
    }>
  } = null;
  private subscription: Subscription;
  private hc: any;
  private cdiv: any;

  constructor(private el: ElementRef, private websocketService: WebsocketService) { }

  ngOnInit() {
    this.hc = this.el.nativeElement.querySelector('div.highcharts');
    this.cdiv = this.el.nativeElement.querySelector('div.c');
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  clickConfiguration() {
    if (this.open) {
      this.removeFundamentalReviewSymbol();
    }
    this.open = !this.open;
    this.el.nativeElement.style.position = this.open ? 'static' : 'fixed';
    if (this.open) {
      setTimeout(() => {
        this.el.nativeElement.scrollIntoView({ behavior: 'smooth' });
      }, 419);
    }
  }

  getFundamental() {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `FUNDAMENTAL_REVIEW: ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }

  removeFundamentalReviewSymbol() {
    this.compound = null;
    this.cdiv.style.width = 'auto';
    this.hc.innerHTML = '';
    this.hc.removeAttribute('style');
  }

  sendMessageToBrowser() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.processReceivedString(this.message, true);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  sendMessageToServer() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.sendMessageThroughWebsocket(this.message);
      setTimeout(() => {
        this.message = null;
      }, 3456)
    }
  }

  serverDebug() {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `DEBUG: ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }

  showOrHideStastiticData() {
    this.showStats = !this.showStats;
    this.websocketService.getSymbolNames().forEach(symbol => {
      this.websocketService.sendMessageThroughSubject(symbol, { SHOW_STASTISTIC_DATA: this.showStats })
    })
  }
}
