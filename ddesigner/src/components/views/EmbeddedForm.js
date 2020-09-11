import React, {Component} from 'react';
import {translate} from "react-i18next";
import {getClassDef} from '../../model.js';
import {Form} from 'antd'
import FieldList from './FieldList'

class EmbeddedForm extends Component {

    constructor(...args) {
        super(...args);
        this.state = {fields: []}
    }

    propsChanged(props) {
        let fields = []
        if (!!props.entity && !!props.entity._type_) {
            const classDef = getClassDef(props.entity._type_)
            if (!!props.actionName) {
                const action = classDef.actions.find(a => a.name === props.actionName)
                if (action) {
                    fields = action.parameters
                }
            }
            else {
                fields = classDef.fields
            }
        }
        this.setState({fields})
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity._type_ !== this.props.entity._type_ || nextProps.entity.e_id !== this.props.entity.e_id || nextProps.actionName !== this.props.actionName) {
            this.propsChanged(nextProps)
        }
    }

    render() {
        return (
            <Form onSubmit={e => e.preventDefault()} layout={"vertical"}>
                <FieldList fields={this.state.fields} {...this.props}/>
            </Form>)
    }
}

export default translate()(EmbeddedForm);
