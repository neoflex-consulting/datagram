import React, { Component } from 'react';
import { translate } from 'react-i18next';
import { Avatar, Row, Col, Card, Menu } from 'antd';
import { getIcon } from './../../utils/meta'
import resource from '../../Resource';
import _ from 'lodash'

const { Meta } = Card

class ApplicationContainer extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            zeppelins: [],
            oozies: [],
            historyAmount: 4,
            zeppelinsAmount: 4,
            ooziesAmount: 4
        }
    }

    componentDidMount() {
        resource.getSimpleSelect("rt.Zeppelin", ["name", "http", "userName", "password"]).then(list => {
            this.setState({ zeppelins: list })
        })
        resource.getSimpleSelect("rt.Oozie", ["name", "http"]).then(list => {
            this.setState({ oozies: list })
        })
    }

    render() {
        const { zeppelins, oozies, historyAmount, zeppelinsAmount, ooziesAmount } = this.state
        const { t, push, pathHistory, justify, startPage } = this.props
        return (
            <div>
            <Row type="flex" justify={justify ? justify : "space-around" }>
            <Col span={4} pull={ startPage ? 4 : 0 }>
                <div className="next-box">
                    <Card key="last" bordered={false} style={{ width: '45vh' }}>
                        <Meta className="card-custom" title={t('lastviewed')} />
                        <Menu
                            onClick={(e) => {
                                push(e.item.props.data)
                            }}
                            className="start-page-menu"
                            mode="inline"
                        >
                            {pathHistory ? _.take(pathHistory, historyAmount).map((p) => {
                                const m = p[p.length - 1]
                                return <Menu.Item key={m.name} data={p}>
                                    <span><Avatar src={getIcon(m)} size={"small"} />&nbsp;<b>{t(`${m._type_}.caption`, { ns: 'classes' }) + ":"}</b>&nbsp;{m.name}</span>
                                </Menu.Item>
                            }) : []}
                        </Menu>
                        {pathHistory && pathHistory.length > 4 && historyAmount === 4 ?
                            <button className="show-more" onClick={(e) => this.setState({ historyAmount: 10 })}>{t('showmore')}</button> : undefined}
                    </Card>
                </div>
            </Col>
            <Col span={4} pull={startPage ? 2 : 0}>
                <div className="next-box">
                    <Card key="zep" bordered={false} style={{ width: '35vh' }}>
                        <Meta className="card-custom" title="ZEPPELIN" />
                        <Menu
                            selectable={false}
                            className="start-page-menu"
                            mode="inline"
                            onClick={(e) => {
                                const {http, userName, password} = e.item.props.data
                                if (!!userName && !!password) {
                                    let form = new URLSearchParams()
                                    form.set("userName", userName)
                                    form.set("password", password)
                                    fetch(http + "/api/login", {
                                        credentials: "include",
                                        method: "POST",
                                        body: form
                                    }).then(resp=>{
                                        window.open(http, "_blank")
                                    })
                                }
                                else {
                                    window.open(http, "_blank")
                                }
                            }}
                        >
                            {zeppelins ? _.take(_.uniqBy(zeppelins, 'name'), zeppelinsAmount).map((m) => {
                                return <Menu.Item key={m.name} data={m}>
                                    <span>{m.name}</span>
                                </Menu.Item>
                             }) : []}
                        </Menu>
                        {zeppelins && zeppelins.length > 4 && zeppelinsAmount === 4 ?
                            <button className="show-more" onClick={(e) => this.setState({ zeppelinsAmount: 10 })}>{t('showmore')}</button> : undefined}
                    </Card>
                </div>
            </Col>
            <Col span={4}>
                <div className="next-box">
                    <Card key="oozie" bordered={false} style={{ width: '35vh' }}>
                        <Meta className="card-custom" title="OOZIE" />
                        <Menu
                            selectable={false}
                            className="start-page-menu"
                            mode="inline"
                        >
                            {oozies ? _.take(_.uniqBy(oozies, 'name'), ooziesAmount).map((m) => {
                                return <Menu.Item key={m.name} data={m}>
                                    <a key={m.name} href={m.http + "/oozie/"} target={"_blank"}>{m.name}</a>
                                </Menu.Item>
                            }) : []}
                        </Menu>
                        {oozies && oozies.length > 4 && ooziesAmount === 4 ?
                            <button className="show-more" onClick={() => this.setState({ ooziesAmount: 10 })}>{t('showmore')}</button> : undefined}
                    </Card>
                </div>
            </Col>
            </Row>
            </div>
        )
    }

}

export default translate()(ApplicationContainer);
