import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import { I18nextProvider } from 'react-i18next'
import i18n from './i18n'
import App from './App';
//import registerServiceWorker from './registerServiceWorker';
import {unregister as unregisterServiceWorker} from './registerServiceWorker';
import { BrowserRouter as Router,  Route } from 'react-router-dom';
import {getBasename} from './utils/meta'

ReactDOM.render(
  <Router basename={getBasename(window.location.pathname)}>
      <div>
          <I18nextProvider i18n={ i18n }>
            <Route path={'/**'} component={App}/>
          </I18nextProvider>
      </div>
  </Router>, document.getElementById('root'));
unregisterServiceWorker();
