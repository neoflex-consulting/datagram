import {Component} from 'react';
import {translate} from 'react-i18next'
import createComponent from './Components';

class Bottom extends Component {
    render() {
        return this.props.components.filter(c => c.id === this.props.panel).map(c => {
                return createComponent(c.name, {key: c.name, ...this.props, ...c.props})
            }
        )
    }
}

export default translate()(Bottom);
