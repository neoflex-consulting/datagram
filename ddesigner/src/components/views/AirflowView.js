import React, { Component } from 'react';
import { translate } from "react-i18next";
import resource from "../../Resource";
import { Form, Row, Col } from 'antd';

class AtlasView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {atlasSchemes: [], projects: []}
    }

    propsChanged(props) {
        resource.callStatic("rt.Airflow", "getPools", {entity: JSON.stringify(this.props.entity)}).then(
            result => {
                this.setState({pools: result})
            })
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity.name !== this.props.entity.name) {
            this.propsChanged(nextProps)
        }
    }

    render() {
        let airflowurl = this.props.entity.http ? new URL(this.props.entity.http): {};
        return (
            <div>
            <Row>
                <Col offset={3} span={5}><b>Airflow Pools</b></Col>
            </Row>
            <Row>
                <Col offset={3} span={5}><b>Pool name</b></Col>
                <Col span={3}><b>Slots</b></Col>
                <Col span={8}><b>Description</b></Col>
            </Row>
                {this.state.pools &&
                    this.state.pools.map(pool=>(
                        <Row>
                            <Col offset={3} span={5}><a href={airflowurl.origin + "/admin/pool/edit/?url=%2Fadmin%2Fpool%2F&id=" + pool.id} target="blank">{pool.pool}</a></Col>
                            <Col span={3}>{pool.slots}</Col>
                            <Col span={8}>{pool.description}</Col>
                        </Row>))
                }
                <Row>
                    <Col offset={3}>
                        <Form layout="inline">
                            <Form.Item label="Goto Airflow admin">
                                <a href={airflowurl.origin + "/admin/pool/"} target="blank">
                                    Airflow Edit Pools
                                </a>
                            </Form.Item>
                        </Form>
                    </Col>
                </Row>
            </div>
        )
    }
}

export default translate()(AtlasView);
