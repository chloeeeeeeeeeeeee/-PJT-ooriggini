import React, { Fragment } from "react";
import {
  Container,
  Row,
  Col,
  Card,
  CardHeader,
  CardBody,
  Button,
//   Form,
//   FormGroup,
} from "reactstrap";
import { Link } from "react-router-dom";
// import mainImage from '../../assets/images/mainImage.PNG';



function Aboutus() {
    return (
        <Fragment>
            <Container fluid={true}>
                <Row>
                    <Col sm="12">
                        <Card className="b-r-0">
                        <CardHeader>
                            <input
                                className="Typeahead-input form-control-plaintext"
                                type="text"
                                placeholder="여기에 우리끼니를 검색하세요"
                                defaultValue={""}
                                // onChange={}
                            />
                            <a href="/map"><Button color="warning" size="lg">
                                지도보기(아동view)
                            </Button></a>
                            <a href="/support"><Button color="warning" size="lg">
                                후원하기(후원자view)
                            </Button></a>
                        </CardHeader>
                        <CardBody>
                            {/* <img src={mainImage} className="mainImage"/> */}
                            <h2>프로님 그 얘기 아시잖아요,</h2>
                            <p className='mb-0 aboutUsText'>
                             예전에 우리 커피나... 그런거 먹으러갈때~~ 뒷사람을 위해서 한 잔을 사두면 그 다음사람이 와서 계산하려고 봤는데 이미 계산돼서 그냥 들고 가시면 됩니다, 라고 말을 들었을 때 거기에 대해서 서로 지구공동체니... 지구촌 한가족 흔한 커피 한잔이지만, 내 선행으로 인해 다음 사람이 하루종일 행복하게 만들어질 수 있는 삶의 원동력이 된다 같은 얘기를 하면서 마음 따듯해지는 카페가 한 곳 있었어요. 
                             <br/>
                             저희는 거기서 아이디어를 얻어서 우리가 한그릇을 사 먹고 기분이 좋아서 한그릇 더 결제하면 결국 이 기부를 받은 사람, 아이들이 나중에 커서 다른 기부를 낳는다 다음 사람이 결제할 수 있는 시스템을 만들어보자. 
                             <br/>
                             <span className="font-weight-bold">커피가 아니라 식사가 된다면 행복하지 않을까. </span>
                             <br/>
                             <span className="font-weight-bold">대상이 밥을 잘 못 먹는 사람 정확히는 결식아동, 아동급식카드 소지자이면 기부의 의미가 잘 드러나지 않을까. </span>
                             <br/>
                             생각해보니 다른 가게들은 아동급식카드를 보여주면 결제없이 맛있게 먹고 가면 된다, 그렇게 써놓는 가게들도 많다. 그런 것을 생각해보고 그 아이들이 카드를 내밀 때 부끄러워하는 것 다른 사람의 시선이 문제는 아니고, 내 자신이 신경쓰이지 않도록 들을 부끄러워한다. 그 아이들에게 도움이 되지 않을까. 그냥 다음 사람을 위해 기부하세요! 보다는 결식아동들을 위해 한그릇을 결제하게 만들어서 더 많은 사람들의 참여를 유도하면 좋지 않을까. 
                            </p>
                            <h4 className="font-weight-bold">결식아동들을 위한 키오스크를 만들어보려고 한다.</h4>
                            <p className="text-right aboutUsText">A102 팀장 <span className="font-weight-bold">천석희</span> 님</p>
                        </CardBody>
                        </Card>
                    </Col>
                </Row>
            </Container>
        </Fragment>
    );
}

export default Aboutus;