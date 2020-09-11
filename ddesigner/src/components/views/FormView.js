import React, { Component } from 'react';
import {translate} from "react-i18next";
import { Row, Col } from 'antd'
import EmbeddedForm from './EmbeddedForm'

class FormView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {}
    }

    render() {
        const entity = Object.assign({}, this.props.activeObject, this.props.entity)
        return (
            <Row gutter={24}>
                <Col span={2}/>
                <Col span={20}><EmbeddedForm {...{...this.props, entity}}/></Col>
                <Col span={2}/>
            </Row>
        )
    }
}

export default translate()(FormView);
