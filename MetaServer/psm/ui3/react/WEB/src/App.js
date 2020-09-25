import React, { Component } from 'react';
import './App.css';
import 'bootstrap/dist/css/bootstrap.css'
import 'font-awesome/css/font-awesome.css'
import Application from './Application.js'
import {BrowserRouter as Router, Route } from 'react-router-dom'

class App extends Component {
    render() {
        return (
            <Router>
                <Route exact path={'/:module?/:e_package?/:e_class?/:e_operation?/:e_id?/'} component={Application}/>
            </Router>
        );
    }
}

export default App;
