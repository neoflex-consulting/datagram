import React, {Component} from 'react';
import {Input} from 'antd'
import {translate} from "react-i18next";
import _ from 'lodash'

class Debounced extends Component {
    constructor(...args) {
        super(...args);
        this.onChange = _.debounce(this.onChange.bind(this), 500)
        const {value} = this.props
        this.state = {value}
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        if (nextProps && prevState && prevState.value !== nextProps.value) {
            if (prevState.dirty !== true) {
                return {value: nextProps.value}
            }
        }
        return null
    }

    componentDidUpdate(prevProps, prevState) {
    }

    onChange(e) {
        const {onChange} = this.props
        if (onChange) {
            onChange(e)
        }
        this.setState({dirty: false})
    }

    render() {
        const props = {
            ..._.omit(this.props, ['Component', 'onChange', 't', 'value', 'tReady']),
            value: this.state.value,
            onChange: (e) => {
                const {value} = e.target
                this.setState({value, dirty: true})
                this.onChange({target: {value}})
            }
        }
        const Component = this.props.Component || Input
        return <Component {...props}/>
    }
}

export default translate()(Debounced);
