import {Component, ElementRef, OnDestroy, OnInit} from '@angular/core';
import {WebsocketService} from './websocket.service';
import {Subscription} from 'rxjs';
import * as Highcharts from 'highcharts';
import {Gradient} from "highcharts";

@Component({
  selector: 'div.configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css']
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  open = false;
  message: string = null;
  symbol: string = null;
  compound: {
    symbol: string,
    fundamental: {
      open: number,
      low: number,
      high: number,
      high52weeks: number,
      low52weeks: number,
      dividendYield: number,
      description: string,
      yearFound: number
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
  private chart: any;

  constructor(private el: ElementRef, private websocketService: WebsocketService) { }

  ngOnInit() {
    this.subscription = this.websocketService.getSubject('FUNDAMENTAL_REVIEW').asObservable().subscribe(message => {
      const i = message.text.indexOf(': ');
      if (i == -1) {
        console.error(`Unknown message ${message.text}`);
      }
      else {
        this.compound = JSON.parse(message.text.substr(i + 2));
        this.compound.symbol = message.text.substring(0, i);
        const highchartsData = this.compound.quotes.map(q => {
          const a = q.beginsAt.split(/[TZ:-]/);
          return [Date.UTC(parseInt(a[0]), parseInt(a[1])-1, parseInt(a[2]), parseInt(a[3]), parseInt(a[4]), parseInt(a[5])), q.openPrice];
        });
        this.chart.series[0].setData(highchartsData);
      }
    });

    const hc = this.el.nativeElement.querySelector('div.highcharts');
    const colorIndex = 0;
    this.chart = Highcharts.chart(hc, {
      chart: { zoomType: 'x' },
      credits: { enabled: false },
      plotOptions: {
        area: {
          fillColor: {
            linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1},
            stops: [
              [0, Highcharts.getOptions().colors[colorIndex]],
              [1, this.hexToRGBA(Highcharts.getOptions().colors[colorIndex], 0)]
            ]
          },
          lineWidth: 1,
          states: { hover: { lineWidth: 1 }},
          threshold: null
        }
      },
      title: { text: null },
      xAxis: { type: 'datetime' },
      yAxis: { title: { text: null } },
      legend: { enabled: false },
      series: [{
        type: 'area',
        name: 'seriesX',
        color: Highcharts.getOptions().colors[0],
        data: []
      }]
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  addToWatchList(symbol: string) {
    if (symbol != null && symbol.trim() != '') {
      this.message = `WATCHLIST_ADD: ${symbol}`;
      this.sendMessageToServer();
    }
  }

  clickConfiguration() {
    this.open = !this.open;
    this.el.nativeElement.style.position = this.open ? 'static' : 'fixed';
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

  sendMessageToBrowser() {
    if (this.message != null && this.message.trim() != '') {
      this.websocketService.processReceivedString(this.message);
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

  setDebug(debug: boolean) {
    if (this.symbol != null && this.symbol.trim() != '') {
      this.message = `${debug ? 'DEBUG_ON:' : 'DEBUG_OFF:'} ${this.symbol}`;
      this.sendMessageToServer();
      setTimeout(() => {
        this.symbol = null;
      }, 3456);
    }
  }

  private hexToRGBA(hex: string|Gradient, opacity: number): string {
    if (typeof hex === 'string') {
      const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
      return 'rgba(' + parseInt(result[1], 16) + ', ' + parseInt(result[2], 16) + ', ' + parseInt(result[3], 16) +
        ', ' + opacity + ')';
    }
    return 'rgba(255, 255, 255, 0)';
  }
}
